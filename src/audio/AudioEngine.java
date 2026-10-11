package audio;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * Real-time synth engine with sample-playback support.
 *
 * <p>One mixer thread owns ONE {@link SourceDataLine} and renders every active voice
 * block-by-block (~11 ms), so notes mix together instead of each opening
 * its own audio line. Each synth voice has its own {@link SweepFilter} instance.</p>
 *
 * <h3>Sample playback</h3>
 * <p>When a WAV file is loaded via {@link #loadSongData(File)}, the engine slices
 * the decoded PCM into 16 equal chunks. Calling {@link #playSample(int)} queues the
 * corresponding chunk for playback through the same mixer, so imported audio and
 * synthesized tones can coexist.</p>
 */
public class AudioEngine {

    public enum Waveform {
        SINE(1.0), TRIANGLE(1.0), SAWTOOTH(0.7), SQUARE(0.6), NOISE(0.5);

        private final double defaultGain;

        Waveform(double gain) {
            this.defaultGain = gain;
        }

        public double getDefaultGain() {
            return defaultGain;
        }
    }

    // ---- Audio format & tuning ------------------------------------------
    private static final int SAMPLE_RATE = 44100;
    private static final int BYTES_PER_FRAME = 2;      // 16-bit mono
    private static final int NUM_STEPS = 16;
    private static final int MAX_VOICES = 8;            // oldest voice is stolen beyond this
    private static final int BLOCK_FRAMES = 512;        // render block (~11.6 ms)
    private static final int LINE_BUFFER_MS = 60;       // lower = less latency, higher = safer
    private static final int FADE_FRAMES = (int) (SAMPLE_RATE * 0.01); // 10 ms anti-click fade
    private static final double MASTER_GAIN = 0.5;      // headroom when voices stack

    /** The mixer's output format: 16-bit signed mono, big-endian, 44 100 Hz. */
    private static final AudioFormat FORMAT = new AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        SAMPLE_RATE,
        16,
        1,
        BYTES_PER_FRAME,
        SAMPLE_RATE,
        true // big-endian
    );

    private static final ConcurrentLinkedQueue<Voice> pending = new ConcurrentLinkedQueue<>();
    private static volatile boolean running = true;
    private static final Thread mixerThread = new Thread(AudioEngine::mixLoop, "AudioEngine-Mixer");

    // ---- Oscilloscope waveform snapshot buffer --------------------------
    private static final int SNAPSHOT_SIZE = 256;
    private static final float[] snapshotBuffer = new float[SNAPSHOT_SIZE];
    private static final Object snapshotLock = new Object();

    /**
     * Copies the latest audio output waveform samples into the provided array.
     * Non-blocking and thread-safe.
     *
     * @param dest array to receive the normalized samples (range approx -1.0 to 1.0)
     */
    public static void getWaveformSnapshot(float[] dest) {
        if (dest == null) return;
        synchronized (snapshotLock) {
            int len = Math.min(dest.length, snapshotBuffer.length);
            System.arraycopy(snapshotBuffer, 0, dest, 0, len);
        }
    }

    // ---- Song sample data (guarded by songLock) -------------------------
    private static final Object songLock = new Object();
    /** 16 pre-sliced PCM chunks in the mixer's output format (big-endian 16-bit mono). */
    private static byte[][] songSlices;

    static {
        mixerThread.setDaemon(true);
        mixerThread.start();
    }

    private AudioEngine() {
        // Utility class
    }

    // ---- Public synth API -----------------------------------------------

    public static void playTone(double frequency, double durationMs, Waveform waveform, double volume) {
        if (!running || waveform == null) return;
        // Reject NaN, non-positive and above-Nyquist values instead of crashing later
        if (!(frequency > 0) || frequency >= SAMPLE_RATE / 2.0) return;
        if (!(durationMs > 0) || Double.isNaN(volume)) return;

        volume = Math.max(0.0, Math.min(1.0, volume));
        pending.add(new SynthVoice(waveform, frequency, durationMs, volume));
    }

    public static void playTone(double frequency, double durationMs, Waveform waveform) {
        if (waveform == null) return;
        playTone(frequency, durationMs, waveform, waveform.getDefaultGain());
    }

    public static void playTone(double frequency, double durationMs, boolean isSquareWave) {
        playTone(frequency, durationMs, isSquareWave ? Waveform.SQUARE : Waveform.SINE);
    }

    // ---- Public sample-playback API -------------------------------------

    /**
     * Loads a WAV file, decodes it to 16-bit signed mono PCM at 44 100 Hz,
     * and pre-slices the audio into 16 equal chunks ready for playback.
     *
     * @param wavFile the .wav file to load
     * @throws IOException                   if the file cannot be read
     * @throws UnsupportedAudioFileException if the format is not supported
     */
    public static void loadSongData(File wavFile) throws IOException, UnsupportedAudioFileException {
        if (wavFile == null || !wavFile.exists()) {
            throw new IOException("WAV file is null or does not exist.");
        }

        // Target format matches the mixer's output: 16-bit signed mono, 44 100 Hz.
        // We decode to big-endian here so no further conversion is needed at playback.
        AudioFormat targetFormat = FORMAT;

        byte[] pcmBytes;

        try (AudioInputStream sourceStream = AudioSystem.getAudioInputStream(wavFile)) {
            AudioFormat srcFmt = sourceStream.getFormat();

            // Intermediate step: first convert to 16-bit signed PCM (keeping channels/endianness).
            // Then, if needed, AudioSystem can convert sample rate, channels, or endianness.
            AudioFormat decodedFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                srcFmt.getSampleRate(),
                16,
                srcFmt.getChannels(),
                srcFmt.getChannels() * 2,
                srcFmt.getSampleRate(),
                false // little-endian (standard for WAV)
            );

            AudioInputStream decodedStream = sourceStream;
            if (!srcFmt.matches(decodedFormat) && AudioSystem.isConversionSupported(decodedFormat, srcFmt)) {
                decodedStream = AudioSystem.getAudioInputStream(decodedFormat, sourceStream);
            }

            // Now convert to the mixer's mono big-endian 44100 Hz format
            AudioInputStream mixerStream;
            AudioFormat decodedActual = decodedStream.getFormat();
            if (!decodedActual.matches(targetFormat) && AudioSystem.isConversionSupported(targetFormat, decodedActual)) {
                mixerStream = AudioSystem.getAudioInputStream(targetFormat, decodedStream);
            } else {
                // If direct conversion isn't supported, do our own mono-down-mix and endian swap below
                mixerStream = decodedStream;
            }

            // Read all PCM bytes
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] tmp = new byte[8192];
            int n;
            while ((n = mixerStream.read(tmp)) != -1) {
                buffer.write(tmp, 0, n);
            }

            AudioFormat resultFormat = mixerStream.getFormat();

            // If the result isn't already in the mixer format, manually convert
            if (!resultFormat.matches(targetFormat)) {
                pcmBytes = convertToMixerFormat(buffer.toByteArray(), resultFormat);
            } else {
                pcmBytes = buffer.toByteArray();
            }
        }

        // Slice into 16 equal chunks
        int totalFrames = pcmBytes.length / BYTES_PER_FRAME;
        byte[][] slices = new byte[NUM_STEPS][];
        for (int step = 0; step < NUM_STEPS; step++) {
            int startFrame = (int) ((long) step * totalFrames / NUM_STEPS);
            int endFrame   = (int) ((long) (step + 1) * totalFrames / NUM_STEPS);
            int startByte  = startFrame * BYTES_PER_FRAME;
            int endByte    = endFrame * BYTES_PER_FRAME;
            slices[step] = Arrays.copyOfRange(pcmBytes, startByte, endByte);
        }

        synchronized (songLock) {
            songSlices = slices;
        }
    }

    /**
     * Queues the audio slice for the given step (0–15) into the mixer for playback.
     * Does nothing if no song is loaded or the step index is out of range.
     *
     * @param stepIndex the sequencer step (0–15) whose audio slice to play
     */
    public static void playSample(int stepIndex) {
        if (!running) return;
        byte[][] slices;
        synchronized (songLock) {
            slices = songSlices;
        }
        if (slices == null || stepIndex < 0 || stepIndex >= slices.length) return;
        byte[] chunk = slices[stepIndex];
        if (chunk == null || chunk.length == 0) return;
        pending.add(new SampleVoice(chunk));
    }

    /**
     * Returns {@code true} if a song's PCM data has been loaded and sliced.
     */
    public static boolean isSongLoaded() {
        synchronized (songLock) {
            return songSlices != null;
        }
    }

    /**
     * Clears the loaded song data so playback reverts to synth-only mode.
     */
    public static void clearSongData() {
        synchronized (songLock) {
            songSlices = null;
        }
    }

    public static void shutdown() {
        running = false;
        mixerThread.interrupt();
    }

    // ---- Format conversion helper ----------------------------------------

    /**
     * Manually converts PCM bytes from the given source format into the mixer's
     * target format (16-bit signed mono, big-endian, 44 100 Hz).
     *
     * <p>Handles mono-down-mixing (stereo → mono) and endian swapping.
     * Sample-rate conversion is not handled here — it relies on AudioSystem
     * having done that upstream.</p>
     */
    private static byte[] convertToMixerFormat(byte[] srcBytes, AudioFormat srcFmt) {
        int srcChannels       = srcFmt.getChannels();
        int srcBitsPerSample  = srcFmt.getSampleSizeInBits();
        int srcBytesPerSample = srcBitsPerSample / 8;
        int srcFrameSize      = srcFmt.getFrameSize() > 0 ? srcFmt.getFrameSize() : srcChannels * srcBytesPerSample;
        boolean srcBigEndian  = srcFmt.isBigEndian();

        int totalFrames = srcBytes.length / srcFrameSize;
        byte[] out = new byte[totalFrames * BYTES_PER_FRAME]; // 2 bytes per mono frame

        for (int f = 0; f < totalFrames; f++) {
            int srcOffset = f * srcFrameSize;
            long monoSum = 0;

            for (int ch = 0; ch < srcChannels; ch++) {
                int chOffset = srcOffset + ch * srcBytesPerSample;
                int sample16;
                if (srcBitsPerSample == 16 && chOffset + 1 < srcBytes.length) {
                    int b0 = srcBytes[chOffset] & 0xFF;
                    int b1 = srcBytes[chOffset + 1] & 0xFF;
                    sample16 = srcBigEndian ? (short) ((b0 << 8) | b1) : (short) ((b1 << 8) | b0);
                } else if (srcBitsPerSample == 8 && chOffset < srcBytes.length) {
                    // 8-bit WAV is unsigned, centre at 128
                    sample16 = ((srcBytes[chOffset] & 0xFF) - 128) * 256;
                } else {
                    sample16 = 0;
                }
                monoSum += sample16;
            }

            short monoSample = (short) (monoSum / Math.max(srcChannels, 1));

            // Write as big-endian (mixer format)
            int outOffset = f * 2;
            out[outOffset]     = (byte) ((monoSample >> 8) & 0xFF);
            out[outOffset + 1] = (byte) (monoSample & 0xFF);
        }
        return out;
    }

    // ---- Mixer thread ----------------------------------------------------

    private static void mixLoop() {
        List<Voice> active = new ArrayList<>();
        float[] mix = new float[BLOCK_FRAMES];
        byte[] out = new byte[BLOCK_FRAMES * BYTES_PER_FRAME];
        int lineBufferBytes = (SAMPLE_RATE * LINE_BUFFER_MS / 1000) * BYTES_PER_FRAME;
        SourceDataLine line = null;

        try {
            line = AudioSystem.getSourceDataLine(FORMAT);
            line.open(FORMAT, lineBufferBytes);
            line.start();

            while (running) {
                // Pick up newly triggered notes
                Voice v;
                while ((v = pending.poll()) != null) {
                    if (active.size() >= MAX_VOICES) active.remove(0); // voice stealing
                    active.add(v);
                }

                // Render and mix all voices; finished voices are removed
                Arrays.fill(mix, 0f);
                active.removeIf(voice -> voice.render(mix));

                // Soft-clip (tanh) and convert to 16-bit big-endian PCM
                for (int i = 0; i < BLOCK_FRAMES; i++) {
                    double s = Math.tanh(mix[i] * MASTER_GAIN);
                    short pcm = (short) (s * 32767.0);
                    out[i * 2]     = (byte) ((pcm >> 8) & 0xFF);
                    out[i * 2 + 1] = (byte) (pcm & 0xFF);
                }

                // Update visualizer snapshot buffer for real-time oscilloscope
                synchronized (snapshotLock) {
                    for (int i = 0; i < SNAPSHOT_SIZE; i++) {
                        int srcIndex = (i * BLOCK_FRAMES) / SNAPSHOT_SIZE;
                        snapshotBuffer[i] = (float) Math.tanh(mix[srcIndex] * MASTER_GAIN);
                    }
                }

                // Blocks when the line buffer is full, which paces the loop
                line.write(out, 0, out.length);
            }
        } catch (LineUnavailableException | IllegalArgumentException e) {
            System.err.println("AudioEngine: could not open audio line: " + e.getMessage());
        } finally {
            running = false;
            pending.clear();
            if (line != null) {
                line.stop();
                line.close();
            }
        }
    }

    // ---- Voice (base class for mixer rendering) --------------------------

    /**
     * Abstract voice that can be rendered into the mixer's float buffer.
     * Both synthesized tones and PCM samples extend this.
     */
    private static sealed class Voice permits SynthVoice, SampleVoice {
        /** Adds this voice into the mix buffer. Returns true when the voice has finished. */
        boolean render(float[] mix) {
            return true; // default: immediately finished
        }
    }

    // ---- SynthVoice (original waveform synthesizer) ----------------------

    private static final class SynthVoice extends Voice {
        private final Waveform waveform;
        private final double phaseInc;
        private final double volume;
        private final int totalFrames;
        private final int fadeFrames;
        private final SweepFilter filter = new SweepFilter();

        private double phase = 0.0;
        private int frame = 0;

        SynthVoice(Waveform waveform, double frequency, double durationMs, double volume) {
            this.waveform = waveform;
            this.phaseInc = frequency / SAMPLE_RATE;
            this.volume = volume;
            this.totalFrames = Math.max(1, (int) (SAMPLE_RATE * (durationMs / 1000.0)));
            // Short notes get a shorter fade so fade-in and fade-out never overlap
            this.fadeFrames = Math.min(FADE_FRAMES, totalFrames / 2);
        }

        /** Adds this voice into the mix buffer. Returns true when the voice has finished. */
        @Override
        boolean render(float[] mix) {
            for (int i = 0; i < mix.length && frame < totalFrames; i++, frame++) {
                double sample = generateSample();
                sample = (double) filter.process((float) sample);

                double envelope = 1.0;
                if (fadeFrames > 0) {
                    int edgeDistance = Math.min(frame, totalFrames - frame);
                    envelope = Math.min(1.0, (double) edgeDistance / fadeFrames);
                }

                mix[i] += (float) (sample * volume * envelope);

                phase += phaseInc;
                if (phase >= 1.0) phase -= 1.0;
            }
            return frame >= totalFrames;
        }

        private double generateSample() {
            return switch (waveform) {
                case SINE -> Math.sin(2.0 * Math.PI * phase);
                case TRIANGLE -> 2.0 * Math.abs(2.0 * (phase - Math.floor(phase + 0.5))) - 1.0;
                case SAWTOOTH -> 2.0 * (phase - Math.floor(phase + 0.5));
                case SQUARE -> phase < 0.5 ? 1.0 : -1.0;
                case NOISE -> ThreadLocalRandom.current().nextDouble(-1.0, 1.0);
            };
        }
    }

    // ---- SampleVoice (PCM chunk playback) --------------------------------

    /**
     * Plays a pre-decoded PCM byte chunk through the mixer.
     *
     * <p>The chunk is expected to be in the mixer's output format (16-bit signed,
     * big-endian, mono, 44 100 Hz). Each call to {@link #render(float[])} converts
     * a block of PCM samples to floats and adds them into the mix buffer, with
     * short fade-in/fade-out ramps to prevent clicks.</p>
     */
    private static final class SampleVoice extends Voice {
        private final byte[] pcmData;
        private final int totalFrames;
        private final int fadeFrames;
        private int frame = 0;

        SampleVoice(byte[] pcmData) {
            this.pcmData = pcmData;
            this.totalFrames = pcmData.length / BYTES_PER_FRAME;
            this.fadeFrames = Math.min(FADE_FRAMES, totalFrames / 2);
        }

        @Override
        boolean render(float[] mix) {
            for (int i = 0; i < mix.length && frame < totalFrames; i++, frame++) {
                // Decode big-endian 16-bit signed sample to float [-1.0, 1.0]
                int byteOffset = frame * 2;
                int hi = pcmData[byteOffset] & 0xFF;
                int lo = pcmData[byteOffset + 1] & 0xFF;
                short sample16 = (short) ((hi << 8) | lo);
                float sample = sample16 / 32768.0f;

                // Apply fade envelope to prevent clicks at start/end
                float envelope = 1.0f;
                if (fadeFrames > 0) {
                    int edgeDistance = Math.min(frame, totalFrames - 1 - frame);
                    envelope = Math.min(1.0f, (float) edgeDistance / fadeFrames);
                }

                mix[i] += sample * envelope;
            }
            return frame >= totalFrames;
        }
    }
}
package audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Small polyphonic tone generator built on a pool of pre-opened audio lines.
 * <ul>
 *   <li>Up to {@value #POOL_SIZE} simultaneous notes; extra notes are dropped.</li>
 *   <li>Sine, triangle, saw, square and noise waveforms (band-limited saw/square).</li>
 *   <li>Full ADSR envelope, so notes start and end without clicks.</li>
 *   <li>Generated buffers are cached, so repeated notes cost almost nothing.</li>
 * </ul>
 *
 * Call {@link #shutdown()} when finished to release the audio lines.
 */
public final class AudioEngine {

    /** Available oscillator shapes. The loudness factor roughly evens out perceived volume. */
    public enum Waveform {
        SINE(1.0), TRIANGLE(1.0), SAW(0.7), SQUARE(0.6), NOISE(0.5);

        private final double loudness;

        Waveform(double loudness) {
            this.loudness = loudness;
        }
    }

    // ---- Audio format & pool -------------------------------------------------------------
    private static final int SAMPLE_RATE = 44100;
    private static final int BYTES_PER_FRAME = 2;             // 16-bit mono
    private static final int POOL_SIZE = 8;                  // Maximum simultaneous notes
    private static final int LINE_BUFFER_MS = 50;             // Lower = less latency, more underrun risk
    private static final double MAX_DURATION_MS = 10_000;

    // ---- Mixing & envelope ---------------------------------------------------------------
    // Eight voices are summed by the system mixer, so each one leaves headroom to avoid clipping.
    private static final double VOICE_GAIN = 0.3;
    private static final double ATTACK_MS = 5.0;
    private static final double DECAY_MS = 30.0;
    private static final double SUSTAIN_LEVEL = 0.8;
    private static final double RELEASE_MS = 25.0;

    // ---- Filter Hook ---------------------------------------------------------------------
    private static final SweepFilter filter = new SweepFilter();

    public static SweepFilter getFilter() {
        return filter;
    }

    // ---- Buffer cache --------------------------------------------------------------------
    private static final int MAX_CACHE_ENTRIES = 128;

    /** One reusable audio channel. */
    private static final class Voice {
        final SourceDataLine line;
        final AtomicBoolean busy = new AtomicBoolean(false);

        Voice(SourceDataLine line) {
            this.line = line;
        }
    }

    private static final List<Voice> voices;
    private static final ExecutorService executor;
    private static volatile boolean closed;

    /** LRU cache of rendered tones, keyed by waveform, frequency, length and gain. */
    private static final Map<String, byte[]> cache = Collections.synchronizedMap(
            new LinkedHashMap<String, byte[]>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            });

    // Opens the voice pool once. A failure (no sound card, headless server, ...) leaves the
    // engine in a silent-but-safe state instead of throwing from the static initializer.
    static {
        List<Voice> opened = new ArrayList<>();
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, true);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        int bufferBytes = SAMPLE_RATE * BYTES_PER_FRAME * LINE_BUFFER_MS / 1000;

        for (int i = 0; i < POOL_SIZE; i++) {
            try {
                SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(format, bufferBytes);
                line.start(); // Keep channels continuously running
                opened.add(new Voice(line));
            } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
                System.err.println("AudioEngine: opened " + i + " of " + POOL_SIZE
                        + " voices (" + e.getMessage() + ")");
                break;
            }
        }
        voices = Collections.unmodifiableList(opened);

        AtomicInteger threadId = new AtomicInteger();
        // A voice is acquired before a task is submitted, so at most voices.size() tasks
        // ever run at once and none of them have to wait in a queue.
        executor = Executors.newFixedThreadPool(Math.max(1, voices.size()), r -> {
            Thread t = new Thread(r, "audio-voice-" + threadId.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    private AudioEngine() {
    }

    // ==== Public API ====================================================================

    /** @return true if at least one audio line was opened and the engine is still running. */
    public static boolean isAvailable() {
        return !closed && !voices.isEmpty();
    }

    /** Backwards-compatible entry point: sine or square at full volume. */
    public static void playTone(double frequency, double durationMs, boolean isSquare) {
        playTone(frequency, durationMs, isSquare ? Waveform.SQUARE : Waveform.SINE, 1.0);
    }

    public static void playTone(double frequency, double durationMs, Waveform waveform) {
        playTone(frequency, durationMs, waveform, 1.0);
    }

    /**
     * Plays a tone without blocking the caller. If every voice is busy the note is dropped.
     *
     * @param frequency  pitch in Hz (ignored for {@link Waveform#NOISE})
     * @param durationMs length in milliseconds (1 to 10,000)
     * @param waveform   oscillator shape
     * @param volume     amplitude scalar (0.0 to 1.0)
     */
    public static void playTone(double frequency, double durationMs, Waveform waveform, double volume) {
        if (!isAvailable() || durationMs <= 0) {
            return;
        }

        double clampedDuration = Math.min(durationMs, MAX_DURATION_MS);
        double clampedVolume = Math.max(0.0, Math.min(1.0, volume));
        Waveform wf = Objects.requireNonNullElse(waveform, Waveform.SINE);

        Voice availableVoice = acquireVoice();
        if (availableVoice == null) {
            return; // All voices busy, drop note
        }

        try {
            executor.submit(() -> {
                try {
                    byte[] audioBuffer = getOrGenerateBuffer(frequency, clampedDuration, wf, clampedVolume);
                    availableVoice.line.write(audioBuffer, 0, audioBuffer.length);
                } finally {
                    availableVoice.busy.set(false);
                }
            });
        } catch (RejectedExecutionException e) {
            availableVoice.busy.set(false);
        }
    }

    /** Releases all audio resources and shuts down background execution pool. */
    public static void shutdown() {
        if (closed) return;
        closed = true;

        executor.shutdownNow();
        for (Voice v : voices) {
            try {
                v.line.drain();
                v.line.close();
            } catch (Exception ignored) {
            }
        }
        cache.clear();
    }

    // ==== Internal Helpers & Synthesis ===================================================

    private static Voice acquireVoice() {
        for (Voice v : voices) {
            if (v.busy.compareAndSet(false, true)) {
                return v;
            }
        }
        return null;
    }

    private static byte[] getOrGenerateBuffer(double freq, double durationMs, Waveform wf, double volume) {
        String key = String.format("%s_%.1f_%.1f_%.2f", wf.name(), freq, durationMs, volume);
        return cache.computeIfAbsent(key, k -> generateAudioBuffer(freq, durationMs, wf, volume));
    }

    private static byte[] generateAudioBuffer(double freq, double durationMs, Waveform wf, double volume) {
        int totalFrames = (int) Math.round((SAMPLE_RATE * durationMs) / 1000.0);
        byte[] buffer = new byte[totalFrames * BYTES_PER_FRAME];

        int attackFrames = (int) Math.round((SAMPLE_RATE * ATTACK_MS) / 1000.0);
        int decayFrames = (int) Math.round((SAMPLE_RATE * DECAY_MS) / 1000.0);
        int releaseFrames = (int) Math.round((SAMPLE_RATE * RELEASE_MS) / 1000.0);
        int sustainFrames = Math.max(0, totalFrames - attackFrames - decayFrames - releaseFrames);

        double masterGain = VOICE_GAIN * wf.loudness * volume;

        for (int frame = 0; frame < totalFrames; frame++) {
            double envelope = calculateEnvelope(frame, totalFrames, attackFrames, decayFrames, sustainFrames, releaseFrames);
            double sampleValue = generateSample(wf, freq, frame) * envelope * masterGain;

            // Apply resonant low-pass sweep filter
            float filteredSample = filter.process((float) sampleValue);

            short pcmSample = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(filteredSample * 32767.0)));

            // Big-endian 16-bit PCM mono
            buffer[frame * 2] = (byte) ((pcmSample >> 8) & 0xFF);
            buffer[frame * 2 + 1] = (byte) (pcmSample & 0xFF);
        }

        return buffer;
    }

    private static double calculateEnvelope(int frame, int totalFrames, int attack, int decay, int sustain, int release) {
        if (frame < attack) {
            return (double) frame / Math.max(1, attack);
        } else if (frame < attack + decay) {
            double progress = (double) (frame - attack) / Math.max(1, decay);
            return 1.0 - progress * (1.0 - SUSTAIN_LEVEL);
        } else if (frame < attack + decay + sustain) {
            return SUSTAIN_LEVEL;
        } else {
            int releaseFrame = frame - (totalFrames - release);
            double progress = (double) releaseFrame / Math.max(1, release);
            return Math.max(0.0, SUSTAIN_LEVEL * (1.0 - progress));
        }
    }

    private static double generateSample(Waveform wf, double freq, int frame) {
        double t = (double) frame / SAMPLE_RATE;
        double phase = (t * freq) % 1.0;

        return switch (wf) {
            case SINE -> Math.sin(2.0 * Math.PI * phase);
            case TRIANGLE -> 2.0 * Math.abs(2.0 * (phase - Math.floor(phase + 0.5))) - 1.0;
            case SAW -> 2.0 * (phase - Math.floor(phase + 0.5));
            case SQUARE -> phase < 0.5 ? 1.0 : -1.0;
            case NOISE -> ThreadLocalRandom.current().nextDouble(-1.0, 1.0);
        };
    }
}
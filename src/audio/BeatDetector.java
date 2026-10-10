package audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * Utility class that performs energy-based beat detection and slicing on .wav audio files
 * for a 16-step sequencer.
 *
 * <p>Uses only standard {@code javax.sound.sampled} libraries with no external dependencies.
 *
 * <h3>How it works:</h3>
 * <ol>
 *   <li>Reads the raw PCM audio data of a {@code .wav} file into memory.</li>
 *   <li>Divides the audio stream into 16 equal time chunks (corresponding to 16 sequencer steps).</li>
 *   <li>Calculates the Root Mean Square (RMS) energy for each chunk:
 *       <code>RMS = sqrt( (1 / N) * sum(sample^2) )</code>.</li>
 *   <li>Computes an average energy threshold across all chunks.</li>
 *   <li>Marks steps with energy exceeding the dynamic threshold as {@code true} (beat/spike)
 *       and quiet steps as {@code false}.</li>
 * </ol>
 */
public final class BeatDetector {

    /** Number of steps in the sequencer. */
    public static final int NUM_STEPS = 16;

    /**
     * Default sensitivity multiplier for dynamic thresholding.
     * Chunks with energy greater than (average energy * 1.05) are flagged as beats.
     */
    public static final double DEFAULT_SENSITIVITY = 1.05;

    /**
     * Minimum RMS energy floor (noise gate) to avoid false positives on near-silent tracks.
     */
    public static final double MIN_ENERGY_FLOOR = 0.01;

    /** Utility class – private constructor prevents instantiation. */
    private BeatDetector() {
    }

    /**
     * Analyzes a {@code .wav} file from the given file path and returns a 16-element boolean array.
     *
     * @param filePath path to the .wav file
     * @return a boolean array of length 16 where true indicates an active beat step
     */
    public static boolean[] analyzeAudio(String filePath) {
        if (filePath == null) {
            return new boolean[NUM_STEPS];
        }
        return analyzeAudio(new File(filePath));
    }

    /**
     * Primary integration hook: Analyzes a {@code .wav} file and returns a 16-element boolean array
     * representing detected beats across the 16 sequencer steps.
     *
     * <p>If an error occurs while reading the audio file (e.g. missing file or unsupported format),
     * this method safely prints a descriptive warning and returns a 16-step array of {@code false}
     * values to prevent UI or playback crashes.</p>
     *
     * @param audioFile the .wav audio file to analyze
     * @return a boolean array of size 16 (true = beat detected, false = quiet)
     */
    public static boolean[] analyzeAudio(File audioFile) {
        return analyzeAudio(audioFile, DEFAULT_SENSITIVITY);
    }

    /**
     * Analyzes a {@code .wav} file using a custom sensitivity multiplier.
     *
     * @param audioFile             the .wav audio file to analyze
     * @param sensitivityMultiplier multiplier applied to average RMS energy (e.g. 1.0 to 1.5)
     * @return a boolean array of size 16 (true = beat detected, false = quiet)
     */
    public static boolean[] analyzeAudio(File audioFile, double sensitivityMultiplier) {
        if (audioFile == null || !audioFile.exists()) {
            System.err.println("BeatDetector: Audio file is null or does not exist.");
            return new boolean[NUM_STEPS];
        }

        try {
            // Step 1: Calculate RMS volume energy for each of the 16 chunks
            double[] chunkEnergies = calculateChunkRms(audioFile);

            // Step 2: Apply dynamic thresholding to determine beat triggers
            return applyDynamicThreshold(chunkEnergies, sensitivityMultiplier);
        } catch (UnsupportedAudioFileException e) {
            System.err.println("BeatDetector: Unsupported audio format in '" + audioFile.getName() + "': " + e.getMessage());
            return new boolean[NUM_STEPS];
        } catch (IOException e) {
            System.err.println("BeatDetector: I/O error reading audio file '" + audioFile.getName() + "': " + e.getMessage());
            return new boolean[NUM_STEPS];
        }
    }

    /**
     * Reads the PCM audio from a WAV file and calculates the Root Mean Square (RMS) energy
     * for each of the 16 equal time chunks.
     *
     * @param audioFile the audio file to inspect
     * @return an array of 16 double values in range [0.0, 1.0] representing chunk energy
     * @throws UnsupportedAudioFileException if the file format is not supported by javax.sound.sampled
     * @throws IOException                   if an I/O error occurs reading the audio stream
     */
    public static double[] calculateChunkRms(File audioFile) throws UnsupportedAudioFileException, IOException {
        double[] chunkEnergies = new double[NUM_STEPS];

        // 1. Open the audio input stream using standard Java Sound API
        try (AudioInputStream sourceStream = AudioSystem.getAudioInputStream(audioFile)) {
            AudioFormat baseFormat = sourceStream.getFormat();

            // 2. Ensure the stream is in standard signed 16-bit PCM little-endian format.
            // If the source WAV is 8-bit, 24-bit, or another encoding, convert if supported.
            AudioFormat decodedFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(),
                    16,
                    baseFormat.getChannels(),
                    baseFormat.getChannels() * 2, // 2 bytes per sample per channel
                    baseFormat.getSampleRate(),
                    false // little-endian (standard for WAV)
            );

            AudioInputStream pcmStream = sourceStream;
            if (!baseFormat.matches(decodedFormat) && AudioSystem.isConversionSupported(decodedFormat, baseFormat)) {
                pcmStream = AudioSystem.getAudioInputStream(decodedFormat, sourceStream);
            }

            AudioFormat format = pcmStream.getFormat();
            int channels = format.getChannels();
            int sampleSizeInBits = format.getSampleSizeInBits();
            int bytesPerFrame = format.getFrameSize();
            boolean isBigEndian = format.isBigEndian();

            // Safety fallback for frame size
            if (bytesPerFrame <= 0) {
                bytesPerFrame = channels * (sampleSizeInBits / 8);
            }

            // 3. Read all raw PCM bytes into memory
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] transferBuffer = new byte[4096];
            int readBytes;
            while ((readBytes = pcmStream.read(transferBuffer, 0, transferBuffer.length)) != -1) {
                buffer.write(transferBuffer, 0, readBytes);
            }
            byte[] audioBytes = buffer.toByteArray();

            // Calculate total audio frames (each frame holds 1 sample for each channel)
            int totalFrames = audioBytes.length / bytesPerFrame;
            if (totalFrames < NUM_STEPS) {
                // Audio file is empty or too short to slice into 16 steps
                return chunkEnergies;
            }

            // 4. Divide into 16 equal time chunks and compute RMS for each chunk
            for (int step = 0; step < NUM_STEPS; step++) {
                // Determine start and end frame indices for this chunk
                int startFrame = (int) ((long) step * totalFrames / NUM_STEPS);
                int endFrame = (int) ((long) (step + 1) * totalFrames / NUM_STEPS);
                int chunkFrameCount = endFrame - startFrame;

                if (chunkFrameCount <= 0) {
                    chunkEnergies[step] = 0.0;
                    continue;
                }

                double sumOfSquares = 0.0;

                for (int frame = startFrame; frame < endFrame; frame++) {
                    // Extract normalized amplitude (-1.0 to 1.0) for this frame (averaged across channels)
                    double amplitude = extractFrameAmplitude(audioBytes, frame, bytesPerFrame, channels, sampleSizeInBits, isBigEndian);
                    sumOfSquares += (amplitude * amplitude);
                }

                // RMS = Square Root of Mean Squared Amplitude
                double meanSquare = sumOfSquares / chunkFrameCount;
                chunkEnergies[step] = Math.sqrt(meanSquare);
            }
        }

        return chunkEnergies;
    }

    /**
     * Extracts and normalizes the audio sample at the specified frame index to the range [-1.0, 1.0].
     * If the audio is multi-channel (e.g. stereo), samples across all channels are averaged together.
     */
    private static double extractFrameAmplitude(byte[] audioBytes, int frameIndex, int bytesPerFrame,
                                                int channels, int sampleSizeInBits, boolean isBigEndian) {
        int frameStartOffset = frameIndex * bytesPerFrame;
        double sumSample = 0.0;

        for (int ch = 0; ch < channels; ch++) {
            if (sampleSizeInBits == 16) {
                int sampleOffset = frameStartOffset + (ch * 2);
                if (sampleOffset + 1 >= audioBytes.length) {
                    continue;
                }

                int b0 = audioBytes[sampleOffset] & 0xFF;
                int b1 = audioBytes[sampleOffset + 1] & 0xFF;

                short sampleValue;
                if (isBigEndian) {
                    sampleValue = (short) ((b0 << 8) | b1);
                } else {
                    sampleValue = (short) ((b1 << 8) | b0);
                }

                // Normalize 16-bit signed integer (-32768 to 32767) to [-1.0, 1.0]
                sumSample += (sampleValue / 32768.0);

            } else if (sampleSizeInBits == 8) {
                int sampleOffset = frameStartOffset + ch;
                if (sampleOffset >= audioBytes.length) {
                    continue;
                }

                // 8-bit PCM is typically unsigned in WAV (0 to 255, where 128 is center/silence)
                int unsignedByte = audioBytes[sampleOffset] & 0xFF;
                double normalized = (unsignedByte - 128) / 128.0;
                sumSample += normalized;
            }
        }

        return channels > 0 ? (sumSample / channels) : 0.0;
    }

    /**
     * Compares each chunk's RMS energy against a dynamically calculated threshold.
     *
     * <p>Formula:
     * <pre>
     *   averageEnergy = sum(chunkEnergies) / 16
     *   dynamicThreshold = max(averageEnergy * sensitivityMultiplier, MIN_ENERGY_FLOOR)
     *   isBeat = chunkEnergy >= dynamicThreshold and chunkEnergy > MIN_ENERGY_FLOOR
     * </pre>
     *
     * @param chunkEnergies         array of 16 RMS energy values
     * @param sensitivityMultiplier threshold scaling factor
     * @return boolean array of length 16 indicating beats
     */
    public static boolean[] applyDynamicThreshold(double[] chunkEnergies, double sensitivityMultiplier) {
        boolean[] beatPattern = new boolean[NUM_STEPS];
        if (chunkEnergies == null || chunkEnergies.length != NUM_STEPS) {
            return beatPattern;
        }

        // Calculate average energy across all 16 chunks
        double totalEnergy = 0.0;
        for (double energy : chunkEnergies) {
            totalEnergy += energy;
        }
        double averageEnergy = totalEnergy / NUM_STEPS;

        // Dynamic threshold with noise floor protection
        double threshold = averageEnergy * sensitivityMultiplier;
        if (threshold < MIN_ENERGY_FLOOR) {
            threshold = MIN_ENERGY_FLOOR;
        }

        // Compare each chunk against the threshold
        for (int i = 0; i < NUM_STEPS; i++) {
            beatPattern[i] = (chunkEnergies[i] >= threshold) && (chunkEnergies[i] > MIN_ENERGY_FLOOR);
        }

        return beatPattern;
    }
}

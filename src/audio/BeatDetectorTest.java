package audio;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

/**
 * Plain {@code main()}-based test runner for {@link BeatDetector}.
 *
 * <p>Generates programmatic .wav files in temporary memory and verifies that:
 * <ul>
 *   <li>Silent audio outputs all false values.</li>
 *   <li>Constant drone tones without dynamic spikes output all false.</li>
 *   <li>Four-on-the-floor beat pulses (steps 0, 4, 8, 12) are accurately sliced and detected.</li>
 *   <li>Missing/null files are safely handled without unhandled crashes.</li>
 * </ul>
 */
public class BeatDetectorTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== BeatDetector Tests ===\n");

        testNullAndMissingFile();
        testSilentAudio();
        testConstantDrone();
        testFourOnTheFloorPattern();
        testSyncopatedRhythm();
        testRmsValuesRange();

        System.out.println("\n=== Results: " + passed + " passed, " + failed + " failed ===");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void check(String description, boolean condition) {
        if (condition) {
            System.out.println("  [PASS] " + description);
            passed++;
        } else {
            System.err.println("  [FAIL] " + description);
            failed++;
        }
    }

    /**
     * Verifies that null and non-existent files return a safe 16-element false array.
     */
    private static void testNullAndMissingFile() {
        boolean[] resultNull = BeatDetector.analyzeAudio((File) null);
        check("Null file returns 16-length array", resultNull != null && resultNull.length == 16);
        check("Null file returns all false", isAllFalse(resultNull));

        boolean[] resultMissing = BeatDetector.analyzeAudio(new File("non_existent_audio_file_12345.wav"));
        check("Missing file returns 16-length array", resultMissing != null && resultMissing.length == 16);
        check("Missing file returns all false", isAllFalse(resultMissing));
    }

    /**
     * Verifies that a completely silent WAV file produces no beat triggers.
     */
    private static void testSilentAudio() {
        File wav = createSyntheticWav(new double[16], 44100, 1600); // 16 chunks of 100 samples silence
        try {
            boolean[] beats = BeatDetector.analyzeAudio(wav);
            check("Silent audio has 16 steps", beats.length == 16);
            check("Silent audio triggers 0 beats", isAllFalse(beats));
        } finally {
            wav.delete();
        }
    }

    /**
     * Verifies that a continuous drone tone (flat energy) does not trigger false spikes.
     */
    private static void testConstantDrone() {
        double[] chunkAmplitudes = new double[16];
        Arrays.fill(chunkAmplitudes, 0.5); // Constant 50% amplitude in all steps

        File wav = createSyntheticWav(chunkAmplitudes, 44100, 3200);
        try {
            boolean[] beats = BeatDetector.analyzeAudio(wav, 1.1);
            check("Constant drone triggers 0 spikes", isAllFalse(beats));
        } finally {
            wav.delete();
        }
    }

    /**
     * Verifies that a classic 4-on-the-floor beat (loud pulses at steps 0, 4, 8, 12)
     * is sliced and detected accurately.
     */
    private static void testFourOnTheFloorPattern() {
        double[] chunkAmplitudes = new double[16];
        // Steps 0, 4, 8, 12 are loud kick spikes (0.9 amplitude); others are quiet (0.05)
        for (int i = 0; i < 16; i++) {
            chunkAmplitudes[i] = (i % 4 == 0) ? 0.9 : 0.05;
        }

        File wav = createSyntheticWav(chunkAmplitudes, 44100, 3200);
        try {
            boolean[] beats = BeatDetector.analyzeAudio(wav);
            check("Step 0 is detected", beats[0]);
            check("Step 4 is detected", beats[4]);
            check("Step 8 is detected", beats[8]);
            check("Step 12 is detected", beats[12]);

            // Off-beat steps should be false
            check("Step 1 is quiet", !beats[1]);
            check("Step 2 is quiet", !beats[2]);
            check("Step 5 is quiet", !beats[5]);
            check("Step 15 is quiet", !beats[15]);
        } finally {
            wav.delete();
        }
    }

    /**
     * Verifies syncopated beat spikes at custom steps (e.g. 0, 3, 7, 10).
     */
    private static void testSyncopatedRhythm() {
        double[] chunkAmplitudes = new double[16];
        int[] hits = {0, 3, 7, 10};
        for (int h : hits) {
            chunkAmplitudes[h] = 0.85;
        }

        File wav = createSyntheticWav(chunkAmplitudes, 44100, 3200);
        try {
            boolean[] beats = BeatDetector.analyzeAudio(wav);
            for (int i = 0; i < 16; i++) {
                boolean expected = (i == 0 || i == 3 || i == 7 || i == 10);
                check("Step " + i + " matches expected (" + expected + ")", beats[i] == expected);
            }
        } finally {
            wav.delete();
        }
    }

    /**
     * Verifies that calculateChunkRms returns 16 values in [0.0, 1.0].
     */
    private static void testRmsValuesRange() {
        double[] chunkAmplitudes = new double[16];
        chunkAmplitudes[2] = 0.8;
        chunkAmplitudes[6] = 0.4;

        File wav = createSyntheticWav(chunkAmplitudes, 44100, 3200);
        try {
            double[] rms = BeatDetector.calculateChunkRms(wav);
            check("RMS array length is 16", rms.length == 16);
            boolean validRange = true;
            for (double val : rms) {
                if (val < 0.0 || val > 1.0) {
                    validRange = false;
                    break;
                }
            }
            check("All RMS values are within [0.0, 1.0]", validRange);
            check("Step 2 has highest RMS", rms[2] > rms[0] && rms[2] > rms[6]);
        } catch (Exception e) {
            check("calculateChunkRms threw exception: " + e.getMessage(), false);
        } finally {
            wav.delete();
        }
    }

    // -------------------------------------------------------------------------
    // Helper: Generate a temporary synthetic 16-bit PCM mono WAV file
    // -------------------------------------------------------------------------

    private static File createSyntheticWav(double[] chunkAmplitudes, int sampleRate, int totalFrames) {
        try {
            File tempFile = File.createTempFile("beat_detector_test_", ".wav");
            tempFile.deleteOnExit();

            AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false); // 16-bit signed, little-endian
            byte[] audioBytes = new byte[totalFrames * 2]; // 2 bytes per sample

            for (int step = 0; step < 16; step++) {
                int start = (int) ((long) step * totalFrames / 16);
                int end = (int) ((long) (step + 1) * totalFrames / 16);
                double amp = chunkAmplitudes[step];

                for (int f = start; f < end; f++) {
                    // Sine pulse at 220Hz scaled by the chunk's target amplitude
                    double angle = 2.0 * Math.PI * 220.0 * f / sampleRate;
                    short sampleVal = (short) (Math.sin(angle) * amp * 32767.0);

                    // Write 16-bit little-endian
                    audioBytes[f * 2] = (byte) (sampleVal & 0xFF);
                    audioBytes[f * 2 + 1] = (byte) ((sampleVal >> 8) & 0xFF);
                }
            }

            ByteArrayInputStream bais = new ByteArrayInputStream(audioBytes);
            AudioInputStream ais = new AudioInputStream(bais, format, totalFrames);
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, tempFile);

            return tempFile;
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate test WAV", e);
        }
    }

    private static boolean isAllFalse(boolean[] array) {
        for (boolean b : array) {
            if (b) return false;
        }
        return true;
    }
}

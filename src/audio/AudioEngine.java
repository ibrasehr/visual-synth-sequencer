package audio;

/**
 * STUB — belongs to Member 1 (Audio Systems Engineer).
 * This file exists only so MainApp compiles and runs on branch feature/integration
 * while Member 1 finishes their real implementation on feature/audio.
 * Replace this file entirely with Member 1's version before final merge —
 * do not edit the method signature, MainApp depends on it exactly as-is.
 */
public class AudioEngine {
    public static void playTone(double frequency, double durationMs, boolean isSquare) {
        // Member 1 implementation: Math.sin synthesis and javax.sound.sampled audio line output
        System.out.println("[stub] playTone freq=" + frequency + " durMs=" + durationMs + " square=" + isSquare);
    }
}

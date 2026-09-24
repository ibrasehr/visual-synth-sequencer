package audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
* AudioEngine - programmatic tone synthesis for the step sequencer.
*/
public final class AudioEngine {

private static final float SAMPLE_RATE = 44100f;
private static final AudioFormat FORMAT =
        new AudioFormat(SAMPLE_RATE, 16, 1, true, false);

private static final double SINE_GAIN = 0.50;
private static final double SQUARE_GAIN = 0.25;

private static final double ATTACK_MS = 5.0;
private static final double RELEASE_MS = 25.0;
private static final double DECAY_RATE = 3.0;

private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
    Thread t = new Thread(r, "AudioEngine-Voice");
    t.setDaemon(true);
    return t;
});

private AudioEngine() { }

public static void playTone(double frequency, double durationMs, boolean isSquare) {
    if (frequency <= 0 || durationMs <= 0) {
        return;
    }
    POOL.submit(() -> {
        byte[] data = synthesize(frequency, durationMs, isSquare);
        play(data);
    });
}

private static byte[] synthesize(double frequency, double durationMs, boolean isSquare) {
    int numSamples = (int) (SAMPLE_RATE * durationMs / 1000.0);
    byte[] buffer = new byte[numSamples * 2];

    int attackSamples = (int) (SAMPLE_RATE * ATTACK_MS / 1000.0);
    int releaseSamples = (int) (SAMPLE_RATE * RELEASE_MS / 1000.0);
    attackSamples = Math.min(attackSamples, numSamples / 2);
    releaseSamples = Math.min(releaseSamples, numSamples / 2);

    double gain = isSquare ? SQUARE_GAIN : SINE_GAIN;
    double angularStep = 2.0 * Math.PI * frequency / SAMPLE_RATE;

    for (int i = 0; i < numSamples; i++) {
        double s = Math.sin(angularStep * i);
        if (isSquare) {
            s = (s >= 0) ? 1.0 : -1.0;
        }

        double env = Math.exp(-DECAY_RATE * i / numSamples);
        if (i < attackSamples) {
            env *= (double) i / attackSamples;
        }
        int fromEnd = numSamples - 1 - i;
        if (fromEnd < releaseSamples) {
            env *= (double) fromEnd / releaseSamples;
        }

        short sample = (short) (s * env * gain * Short.MAX_VALUE);
        buffer[2 * i] = (byte) (sample & 0xFF);
        buffer[2 * i + 1] = (byte) ((sample >> 8) & 0xFF);
    }
    return buffer;
}

private static void play(byte[] data) {
    try (SourceDataLine line = AudioSystem.getSourceDataLine(FORMAT)) {
        line.open(FORMAT);
        line.start();
        line.write(data, 0, data.length);
        line.drain();
    } catch (LineUnavailableException | IllegalArgumentException e) {
        System.err.println("AudioEngine: could not play tone - " + e.getMessage());
    }
}

public static void main(String[] args) throws InterruptedException {
    double[] cMajor = {261.63, 293.66, 329.63, 349.23, 392.00, 440.00, 493.88, 523.25};
    for (double f : cMajor) {
        AudioEngine.playTone(f, 250, false);
        Thread.sleep(300);
    }
    Thread.sleep(500);
    for (double f : cMajor) {
        AudioEngine.playTone(f, 250, true);
        Thread.sleep(300);
    }
    Thread.sleep(500);
    AudioEngine.playTone(261.63, 800, false);
    AudioEngine.playTone(329.63, 800, false);
    AudioEngine.playTone(392.00, 800, false);
    Thread.sleep(1200);
}
}

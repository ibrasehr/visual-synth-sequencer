package audio;

public class SweepFilter {
    private float cutoff = 1000.0f;
    private float resonance = 0.5f;
    private float lfoRate = 2.0f;
    private float lfoPhase = 0.0f;

    // Filter states
    private float low = 0.0f;
    private float band = 0.0f;
    private float high = 0.0f;

    public void setCutoff(float cutoff) {
        this.cutoff = Math.max(30.0f, Math.min(8000.0f, cutoff));
    }

    public void setResonance(float resonance) {
        this.resonance = Math.max(0.0f, Math.min(0.95f, resonance));
    }

    public void setLfoRate(float lfoRate) {
        this.lfoRate = Math.max(0.0f, Math.min(20.0f, lfoRate));
    }

    /**
     * Processes a single audio sample through the resonant state-variable filter with thread safety.
     */
    public synchronized float process(float in) {
        // Advance LFO phase (assuming 44100 Hz sample rate)
        lfoPhase += lfoRate / 44100.0f;
        if (lfoPhase > 1.0f) {
            lfoPhase -= 1.0f;
        }

        // Modulate cutoff frequency with LFO
        float modulatedCutoff = cutoff * (1.0f + 0.5f * (float) Math.sin(2.0 * Math.PI * lfoPhase));
        float f = 2.0f * (float) Math.sin(Math.PI * Math.min(modulatedCutoff / 44100.0f, 0.49f));
        float q = 1.0f - resonance;

        // Correct Chamberlin SVF calculation order requested by reviewer:
        high = in - low - (q * band);
        band += f * high;
        low += f * band;

        return low;
    }
}
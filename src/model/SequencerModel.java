package model;

import java.io.File;

/**
 * STUB — belongs to Member 3 (Data & Persistence Engineer).
 * This file exists only so MainApp compiles and runs on branch feature/integration
 * while Member 3 finishes their real implementation on feature/data.
 * Replace this file entirely with Member 3's version before final merge —
 * do not edit the method signatures, MainApp and GridPanel depend on them exactly as-is.
 */
public class SequencerModel {
    public static final int ROWS = 8;
    public static final int COLS = 16;

    private final boolean[][] grid = new boolean[ROWS][COLS];
    private final double[] frequencies = {523.25, 493.88, 440.00, 392.00, 349.23, 329.63, 293.66, 261.63}; // C Major Scale

    public boolean isCellActive(int row, int col) {
        return grid[row][col];
    }

    public void toggleCell(int row, int col) {
        grid[row][col] = !grid[row][col];
    }

    public double getFrequency(int row) {
        return frequencies[row];
    }

    public void savePattern(File file) {
        // Member 3 implementation: Pattern file export
        System.out.println("[stub] savePattern -> " + file.getName());
    }

    public void loadPattern(File file) {
        // Member 3 implementation: Pattern file import
        System.out.println("[stub] loadPattern <- " + file.getName());
    }

    // --- Convenience methods MainApp needs; keep these when merging Member 3's real file ---

    public int getBpm() {
        return 120;
    }

    public void setBpm(int bpm) {
        // no-op in stub
    }

    public void clear() {
        for (boolean[] row : grid) {
            java.util.Arrays.fill(row, false);
        }
    }
}

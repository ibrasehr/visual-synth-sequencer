package model;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Core data model for a 16-step × 8-row beat sequencer.
 *
 * <p>Each row maps to a fixed frequency in a C-major scale
 * (row 0 = C5 at the top, row 7 = C4 at the bottom). Each of the
 * 16 columns represents one step in the beat pattern.</p>
 *
 * <p>All grid-access methods are {@code synchronized} so the Swing EDT,
 * a playback timer, and the audio engine can safely share this model.</p>
 *
 * <h3>File format (version 1)</h3>
 * <pre>
 * {
 *   "version": 1,
 *   "rows": 8,
 *   "cols": 16,
 *   "bpm": 120,
 *   "grid": [
 *     "1000100010001000",
 *     ...
 *   ]
 * }
 * </pre>
 *
 * @author Member 3 – Data &amp; Persistence Engineer
 * @see PatternStorage
 * @see PatternFileException
 */
public class SequencerModel {

    /** Number of instrument / pitch rows. */
    public static final int ROWS = 8;

    /** Number of steps (columns) per row. */
    public static final int COLS = 16;

    /** Minimum allowed BPM value. */
    private static final int BPM_MIN = 40;

    /** Maximum allowed BPM value. */
    private static final int BPM_MAX = 240;

    /** Default BPM when the model is first created. */
    private static final int BPM_DEFAULT = 120;

    /**
     * The step grid. {@code grid[row][col] == true} means the cell is active.
     */
    private final boolean[][] grid = new boolean[ROWS][COLS];

    /**
     * Fixed frequencies for each row (C-major, descending from C5 to C4).
     * <ul>
     *   <li>Row 0 – C5  (523.25 Hz)</li>
     *   <li>Row 1 – B4  (493.88 Hz)</li>
     *   <li>Row 2 – A4  (440.00 Hz)</li>
     *   <li>Row 3 – G4  (392.00 Hz)</li>
     *   <li>Row 4 – F4  (349.23 Hz)</li>
     *   <li>Row 5 – E4  (329.63 Hz)</li>
     *   <li>Row 6 – D4  (293.66 Hz)</li>
     *   <li>Row 7 – C4  (261.63 Hz)</li>
     * </ul>
     */
    private final double[] frequencies = {
        523.25, 493.88, 440.00, 392.00,
        349.23, 329.63, 293.66, 261.63
    };

    /** Current tempo in beats per minute (40–240). */
    private int bpm = BPM_DEFAULT;

    // ------------------------------------------------------------------
    // Grid access (all synchronized)
    // ------------------------------------------------------------------

    /**
     * Returns {@code true} if the cell at {@code (row, col)} is active.
     *
     * @param row the row index (0–{@value #ROWS}-1, top to bottom)
     * @param col the column index (0–{@value #COLS}-1, left to right)
     * @return whether the cell is active
     * @throws IllegalArgumentException if {@code row} or {@code col} is out of range
     */
    public synchronized boolean isCellActive(int row, int col) {
        validateCell(row, col);
        return grid[row][col];
    }

    /**
     * Toggles the active state of the cell at {@code (row, col)}.
     *
     * @param row the row index (0–{@value #ROWS}-1)
     * @param col the column index (0–{@value #COLS}-1)
     * @throws IllegalArgumentException if {@code row} or {@code col} is out of range
     */
    public synchronized void toggleCell(int row, int col) {
        validateCell(row, col);
        grid[row][col] = !grid[row][col];
    }

    /**
     * Sets the active state of the cell at {@code (row, col)} to the given value.
     *
     * @param row    the row index (0–{@value #ROWS}-1)
     * @param col    the column index (0–{@value #COLS}-1)
     * @param active {@code true} to activate, {@code false} to deactivate
     * @throws IllegalArgumentException if {@code row} or {@code col} is out of range
     */
    public synchronized void setCell(int row, int col, boolean active) {
        validateCell(row, col);
        grid[row][col] = active;
    }

    /**
     * Resets every cell in the grid to inactive.
     */
    public synchronized void clear() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                grid[r][c] = false;
            }
        }
    }

    /**
     * Returns the frequency (in Hz) assigned to the given row.
     *
     * @param row the row index (0–{@value #ROWS}-1)
     * @return the frequency in Hz
     * @throws IllegalArgumentException if {@code row} is out of range
     */
    public synchronized double getFrequency(int row) {
        if (row < 0 || row >= ROWS) {
            throw new IllegalArgumentException(
                "Row index out of range: " + row + " (valid: 0–" + (ROWS - 1) + ")");
        }
        return frequencies[row];
    }

    // ------------------------------------------------------------------
    // BPM
    // ------------------------------------------------------------------

    /**
     * Returns the current tempo in beats per minute.
     *
     * @return BPM (40–240)
     */
    public synchronized int getBpm() {
        return bpm;
    }

    /**
     * Sets the tempo in beats per minute.
     *
     * @param bpm the new tempo (must be in the range 40–240)
     * @throws IllegalArgumentException if {@code bpm} is outside 40–240
     */
    public synchronized void setBpm(int bpm) {
        if (bpm < BPM_MIN || bpm > BPM_MAX) {
            throw new IllegalArgumentException(
                "BPM out of range: " + bpm + " (valid: " + BPM_MIN + "–" + BPM_MAX + ")");
        }
        this.bpm = bpm;
    }

    // ------------------------------------------------------------------
    // Persistence – save
    // ------------------------------------------------------------------

    /**
     * Saves the current grid and tempo to a JSON file.
     *
     * <p>If the supplied {@code file} does not end with {@code .json},
     * the extension is appended automatically. The output is human-readable,
     * pretty-printed JSON encoded in UTF-8.</p>
     *
     * @param file the destination file (a {@code .json} extension is added if absent)
     * @throws PatternFileException if the file cannot be written
     */
    public synchronized void savePattern(File file) {
        file = ensureJsonExtension(file);

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"version\": 1,\n");
        sb.append("  \"rows\": ").append(ROWS).append(",\n");
        sb.append("  \"cols\": ").append(COLS).append(",\n");
        sb.append("  \"bpm\": ").append(bpm).append(",\n");
        sb.append("  \"grid\": [\n");
        for (int r = 0; r < ROWS; r++) {
            sb.append("    \"");
            for (int c = 0; c < COLS; c++) {
                sb.append(grid[r][c] ? '1' : '0');
            }
            sb.append('"');
            if (r < ROWS - 1) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append("  ]\n");
        sb.append("}\n");

        try (BufferedWriter writer = Files.newBufferedWriter(
                file.toPath(), StandardCharsets.UTF_8)) {
            writer.write(sb.toString());
        } catch (IOException e) {
            throw new PatternFileException(
                "Could not save pattern to \"" + file.getName() + "\": " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------
    // Persistence – load
    // ------------------------------------------------------------------

    /**
     * Loads a grid and (optionally) tempo from a JSON file.
     *
     * <p>The load is <em>atomic</em>: the file is fully parsed and validated
     * into a temporary buffer before the model's internal state is updated.
     * If validation fails, the current grid and BPM are left unchanged.</p>
     *
     * <p>Unknown JSON keys are silently ignored. If the {@code bpm} key is
     * absent, the current BPM is retained.</p>
     *
     * @param file the source file
     * @throws PatternFileException if the file is missing, unreadable, or
     *                              contains an invalid pattern
     */
    public synchronized void loadPattern(File file) {
        if (!file.exists()) {
            throw new PatternFileException(
                "File not found: \"" + file.getName() + "\"");
        }

        String content;
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            content = new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PatternFileException(
                "Could not read file \"" + file.getName() + "\": " + e.getMessage(), e);
        }

        // --- Parse into temporaries (atomic: don't touch fields yet) ---
        boolean[][] tempGrid = new boolean[ROWS][COLS];
        int tempBpm = this.bpm; // keep current if absent

        // Validate rows == 8
        Integer parsedRows = parseIntField(content, "rows");
        if (parsedRows != null && parsedRows != ROWS) {
            throw new PatternFileException(
                "Invalid pattern: expected " + ROWS + " rows but file declares " + parsedRows + ".");
        }

        // Validate cols == 16
        Integer parsedCols = parseIntField(content, "cols");
        if (parsedCols != null && parsedCols != COLS) {
            throw new PatternFileException(
                "Invalid pattern: expected " + COLS + " columns but file declares " + parsedCols + ".");
        }

        // BPM (optional)
        Integer parsedBpm = parseIntField(content, "bpm");
        if (parsedBpm != null) {
            if (parsedBpm < BPM_MIN || parsedBpm > BPM_MAX) {
                throw new PatternFileException(
                    "Invalid BPM in file: " + parsedBpm + " (valid: " + BPM_MIN + "–" + BPM_MAX + ").");
            }
            tempBpm = parsedBpm;
        }

        // Grid strings
        List<String> gridStrings = parseGridArray(content);
        if (gridStrings.size() != ROWS) {
            throw new PatternFileException(
                "Invalid pattern: expected " + ROWS + " grid rows but found " + gridStrings.size() + ".");
        }

        Pattern validRow = Pattern.compile("^[01]{" + COLS + "}$");
        for (int r = 0; r < ROWS; r++) {
            String rowStr = gridStrings.get(r);
            if (!validRow.matcher(rowStr).matches()) {
                if (rowStr.length() != COLS) {
                    throw new PatternFileException(
                        "Invalid pattern: grid row " + r + " has length " + rowStr.length()
                        + " (expected " + COLS + ").");
                }
                throw new PatternFileException(
                    "Invalid pattern: grid row " + r + " contains invalid characters (only '0' and '1' allowed).");
            }
            for (int c = 0; c < COLS; c++) {
                tempGrid[r][c] = (rowStr.charAt(c) == '1');
            }
        }

        // --- All validation passed – commit atomically ---
        for (int r = 0; r < ROWS; r++) {
            System.arraycopy(tempGrid[r], 0, grid[r], 0, COLS);
        }
        this.bpm = tempBpm;
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    /**
     * Validates that {@code row} and {@code col} are within bounds.
     */
    private void validateCell(int row, int col) {
        if (row < 0 || row >= ROWS) {
            throw new IllegalArgumentException(
                "Row index out of range: " + row + " (valid: 0–" + (ROWS - 1) + ")");
        }
        if (col < 0 || col >= COLS) {
            throw new IllegalArgumentException(
                "Column index out of range: " + col + " (valid: 0–" + (COLS - 1) + ")");
        }
    }

    /**
     * Appends ".json" to the file if it doesn't already have that extension.
     */
    private static File ensureJsonExtension(File file) {
        if (!file.getName().toLowerCase().endsWith(".json")) {
            return new File(file.getAbsolutePath() + ".json");
        }
        return file;
    }

    /**
     * Extracts an integer value for a given JSON key using regex.
     *
     * @return the parsed integer, or {@code null} if the key is not present
     */
    private static Integer parseIntField(String json, String key) {
        // Matches e.g.  "bpm": 120  or  "rows" : 8
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Extracts the {@code "grid"} array from the JSON string.
     * Returns the list of row strings (without quotes).
     */
    private static List<String> parseGridArray(String json) {
        List<String> rows = new ArrayList<>();

        // Find the "grid" array
        int gridKeyIdx = json.indexOf("\"grid\"");
        if (gridKeyIdx == -1) {
            throw new PatternFileException(
                "Invalid pattern file: missing \"grid\" field.");
        }

        int bracketOpen = json.indexOf('[', gridKeyIdx);
        if (bracketOpen == -1) {
            throw new PatternFileException(
                "Invalid pattern file: \"grid\" field is not an array.");
        }

        int bracketClose = json.indexOf(']', bracketOpen);
        if (bracketClose == -1) {
            throw new PatternFileException(
                "Invalid pattern file: unterminated \"grid\" array.");
        }

        String arrayContent = json.substring(bracketOpen + 1, bracketClose);

        // Extract each quoted string from the array
        Pattern stringPattern = Pattern.compile("\"([^\"]*)\"");
        Matcher m = stringPattern.matcher(arrayContent);
        while (m.find()) {
            rows.add(m.group(1));
        }

        return rows;
    }
}

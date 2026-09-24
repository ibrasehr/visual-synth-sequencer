package model;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Plain {@code main()}-based tests for {@link SequencerModel}.
 *
 * <p>No JUnit or external libraries required – just compile and run.
 * Uses {@link File#createTempFile} so no junk files are left behind.</p>
 *
 * @author Member 3 – Data &amp; Persistence Engineer
 */
public class SequencerModelTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("=== SequencerModel Tests ===\n");

        testToggleOnOff();
        testBoundsExceptions();
        testFrequencies();
        testBpmGetSet();
        testSetCellAndClear();
        testSaveLoadRoundTrip();
        testLoadWrongRowCount();
        testLoadWrongColumnLength();
        testLoadInvalidCharacters();
        testLoadMissingFile();
        testAutoAppendJsonExtension();
        testLoadPreservesGridOnFailure();
        testLoadBpmOptional();

        System.out.println("\n=== Results: " + passed + " passed, " + failed + " failed ===");
        if (failed > 0) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------
    // Individual tests
    // ------------------------------------------------------------------

    private static void testToggleOnOff() {
        SequencerModel m = new SequencerModel();
        check("toggle: initial state is false", !m.isCellActive(0, 0));
        m.toggleCell(0, 0);
        check("toggle: after first toggle is true", m.isCellActive(0, 0));
        m.toggleCell(0, 0);
        check("toggle: after second toggle is false", !m.isCellActive(0, 0));
    }

    private static void testBoundsExceptions() {
        SequencerModel m = new SequencerModel();

        check("bounds: isCellActive row=-1 throws",
            expectIAE(() -> m.isCellActive(-1, 0)));
        check("bounds: isCellActive row=8 throws",
            expectIAE(() -> m.isCellActive(8, 0)));
        check("bounds: isCellActive col=-1 throws",
            expectIAE(() -> m.isCellActive(0, -1)));
        check("bounds: isCellActive col=16 throws",
            expectIAE(() -> m.isCellActive(0, 16)));

        check("bounds: toggleCell row=-1 throws",
            expectIAE(() -> m.toggleCell(-1, 0)));
        check("bounds: toggleCell col=16 throws",
            expectIAE(() -> m.toggleCell(0, 16)));

        check("bounds: getFrequency row=-1 throws",
            expectIAE(() -> m.getFrequency(-1)));
        check("bounds: getFrequency row=8 throws",
            expectIAE(() -> m.getFrequency(8)));
    }

    private static void testFrequencies() {
        SequencerModel m = new SequencerModel();
        check("freq: row 0 = C5 (523.25)",
            Double.compare(m.getFrequency(0), 523.25) == 0);
        check("freq: row 7 = C4 (261.63)",
            Double.compare(m.getFrequency(7), 261.63) == 0);
    }

    private static void testBpmGetSet() {
        SequencerModel m = new SequencerModel();
        check("bpm: default is 120", m.getBpm() == 120);

        m.setBpm(200);
        check("bpm: set to 200", m.getBpm() == 200);

        check("bpm: too low (39) throws",
            expectIAE(() -> m.setBpm(39)));
        check("bpm: too high (241) throws",
            expectIAE(() -> m.setBpm(241)));

        // boundary values
        m.setBpm(40);
        check("bpm: lower bound 40 accepted", m.getBpm() == 40);
        m.setBpm(240);
        check("bpm: upper bound 240 accepted", m.getBpm() == 240);
    }

    private static void testSetCellAndClear() {
        SequencerModel m = new SequencerModel();
        m.setCell(3, 5, true);
        check("setCell: (3,5) is active", m.isCellActive(3, 5));
        m.setCell(3, 5, false);
        check("setCell: (3,5) deactivated", !m.isCellActive(3, 5));

        m.setCell(0, 0, true);
        m.setCell(7, 15, true);
        m.clear();
        check("clear: (0,0) is false after clear", !m.isCellActive(0, 0));
        check("clear: (7,15) is false after clear", !m.isCellActive(7, 15));
    }

    private static void testSaveLoadRoundTrip() {
        SequencerModel m = new SequencerModel();
        // Create a recognisable pattern
        m.toggleCell(0, 0);
        m.toggleCell(0, 4);
        m.toggleCell(0, 8);
        m.toggleCell(0, 12);
        m.toggleCell(3, 2);
        m.toggleCell(7, 15);
        m.setBpm(140);

        File tmp = null;
        try {
            tmp = File.createTempFile("seq_test_", ".json");
            tmp.deleteOnExit();

            m.savePattern(tmp);

            // Clear and verify it's really empty
            m.clear();
            m.setBpm(120);
            check("round-trip: cleared before load", !m.isCellActive(0, 0));

            m.loadPattern(tmp);
            check("round-trip: (0,0) restored", m.isCellActive(0, 0));
            check("round-trip: (0,4) restored", m.isCellActive(0, 4));
            check("round-trip: (0,8) restored", m.isCellActive(0, 8));
            check("round-trip: (0,12) restored", m.isCellActive(0, 12));
            check("round-trip: (3,2) restored", m.isCellActive(3, 2));
            check("round-trip: (7,15) restored", m.isCellActive(7, 15));
            check("round-trip: (1,0) still false", !m.isCellActive(1, 0));
            check("round-trip: bpm restored to 140", m.getBpm() == 140);
        } catch (IOException e) {
            check("round-trip: temp file creation failed – " + e.getMessage(), false);
        } finally {
            if (tmp != null) tmp.delete();
        }
    }

    private static void testLoadWrongRowCount() {
        SequencerModel m = new SequencerModel();
        m.toggleCell(0, 0); // sentinel

        File tmp = null;
        try {
            tmp = File.createTempFile("seq_test_badrows_", ".json");
            tmp.deleteOnExit();
            writeText(tmp,
                "{\n  \"version\": 1, \"rows\": 8, \"cols\": 16, \"bpm\": 120,\n"
                + "  \"grid\": [\n"
                + "    \"1000000000000000\",\n"
                + "    \"0000000000000000\",\n"
                + "    \"0000000000000000\"\n"  // only 3 rows
                + "  ]\n}\n");

            boolean threw = false;
            try {
                m.loadPattern(tmp);
            } catch (PatternFileException e) {
                threw = true;
            }
            check("wrong row count: throws PatternFileException", threw);
            check("wrong row count: grid unchanged (0,0) still active",
                m.isCellActive(0, 0));
        } catch (IOException e) {
            check("wrong row count: temp file error – " + e.getMessage(), false);
        } finally {
            if (tmp != null) tmp.delete();
        }
    }

    private static void testLoadWrongColumnLength() {
        SequencerModel m = new SequencerModel();
        m.toggleCell(1, 1); // sentinel

        File tmp = null;
        try {
            tmp = File.createTempFile("seq_test_badcols_", ".json");
            tmp.deleteOnExit();
            StringBuilder sb = new StringBuilder();
            sb.append("{ \"version\": 1, \"rows\": 8, \"cols\": 16, \"bpm\": 120, \"grid\": [\n");
            for (int i = 0; i < 7; i++) {
                sb.append("  \"0000000000000000\",\n");
            }
            sb.append("  \"000000000000000\"\n"); // 15 chars – too short
            sb.append("] }");
            writeText(tmp, sb.toString());

            boolean threw = false;
            try {
                m.loadPattern(tmp);
            } catch (PatternFileException e) {
                threw = true;
            }
            check("wrong column length: throws PatternFileException", threw);
            check("wrong column length: grid unchanged (1,1) still active",
                m.isCellActive(1, 1));
        } catch (IOException e) {
            check("wrong column length: temp file error – " + e.getMessage(), false);
        } finally {
            if (tmp != null) tmp.delete();
        }
    }

    private static void testLoadInvalidCharacters() {
        SequencerModel m = new SequencerModel();
        m.toggleCell(2, 2); // sentinel

        File tmp = null;
        try {
            tmp = File.createTempFile("seq_test_badchar_", ".json");
            tmp.deleteOnExit();
            StringBuilder sb = new StringBuilder();
            sb.append("{ \"version\": 1, \"rows\": 8, \"cols\": 16, \"bpm\": 120, \"grid\": [\n");
            for (int i = 0; i < 7; i++) {
                sb.append("  \"0000000000000000\",\n");
            }
            sb.append("  \"000000000000000X\"\n"); // 'X' invalid
            sb.append("] }");
            writeText(tmp, sb.toString());

            boolean threw = false;
            try {
                m.loadPattern(tmp);
            } catch (PatternFileException e) {
                threw = true;
            }
            check("invalid chars: throws PatternFileException", threw);
            check("invalid chars: grid unchanged (2,2) still active",
                m.isCellActive(2, 2));
        } catch (IOException e) {
            check("invalid chars: temp file error – " + e.getMessage(), false);
        } finally {
            if (tmp != null) tmp.delete();
        }
    }

    private static void testLoadMissingFile() {
        SequencerModel m = new SequencerModel();
        boolean threw = false;
        try {
            m.loadPattern(new File("__nonexistent_pattern_12345__.json"));
        } catch (PatternFileException e) {
            threw = true;
        }
        check("missing file: throws PatternFileException", threw);
    }

    private static void testAutoAppendJsonExtension() {
        SequencerModel m = new SequencerModel();
        m.toggleCell(0, 0);

        File tmp = null;
        File actualFile = null;
        try {
            // Create a temp file WITHOUT .json extension
            tmp = File.createTempFile("seq_test_ext_", ".tmp");
            tmp.deleteOnExit();
            // Remove .tmp file and save without extension
            String basePath = tmp.getAbsolutePath().replace(".tmp", "");
            tmp.delete();

            File noExtFile = new File(basePath);
            m.savePattern(noExtFile);

            actualFile = new File(basePath + ".json");
            actualFile.deleteOnExit();

            check("auto-extension: .json file was created", actualFile.exists());

            // Verify it loads back
            SequencerModel m2 = new SequencerModel();
            m2.loadPattern(actualFile);
            check("auto-extension: loaded data matches", m2.isCellActive(0, 0));
        } catch (IOException e) {
            check("auto-extension: temp file error – " + e.getMessage(), false);
        } finally {
            if (tmp != null) tmp.delete();
            if (actualFile != null) actualFile.delete();
        }
    }

    private static void testLoadPreservesGridOnFailure() {
        SequencerModel m = new SequencerModel();
        // Set up a known state
        for (int c = 0; c < SequencerModel.COLS; c++) {
            m.setCell(0, c, true);
        }
        m.setBpm(180);

        File tmp = null;
        try {
            tmp = File.createTempFile("seq_test_atomic_", ".json");
            tmp.deleteOnExit();
            // Write a file with a valid first row but invalid content
            writeText(tmp,
                "{ \"version\": 1, \"rows\": 8, \"cols\": 16, \"bpm\": 90, \"grid\": [\n"
                + "  \"0000000000000000\",\n"  // different from current state
                + "  \"0000000000000000\",\n"
                + "  \"0000000000000000\",\n"
                + "  \"0000000000000000\",\n"
                + "  \"0000000000000000\",\n"
                + "  \"0000000000000000\",\n"
                + "  \"0000000000000000\",\n"
                + "  \"INVALID_ROW_DATA\"\n"   // will cause failure
                + "] }");

            try {
                m.loadPattern(tmp);
            } catch (PatternFileException ignored) {
                // expected
            }

            // Original state should be intact
            check("atomic load: row 0 preserved after failed load",
                m.isCellActive(0, 0));
            check("atomic load: bpm preserved after failed load",
                m.getBpm() == 180);
        } catch (IOException e) {
            check("atomic load: temp file error – " + e.getMessage(), false);
        } finally {
            if (tmp != null) tmp.delete();
        }
    }

    private static void testLoadBpmOptional() {
        SequencerModel m = new SequencerModel();
        m.setBpm(180);

        File tmp = null;
        try {
            tmp = File.createTempFile("seq_test_nobpm_", ".json");
            tmp.deleteOnExit();
            // File with no bpm key
            StringBuilder sb = new StringBuilder();
            sb.append("{ \"version\": 1, \"rows\": 8, \"cols\": 16, \"grid\": [\n");
            for (int i = 0; i < 8; i++) {
                sb.append("  \"0000000000000000\"");
                if (i < 7) sb.append(',');
                sb.append('\n');
            }
            sb.append("] }");
            writeText(tmp, sb.toString());

            m.loadPattern(tmp);
            check("bpm optional: bpm stays 180 when file has no bpm key",
                m.getBpm() == 180);
        } catch (IOException e) {
            check("bpm optional: temp file error – " + e.getMessage(), false);
        } finally {
            if (tmp != null) tmp.delete();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void check(String label, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  PASS  " + label);
        } else {
            failed++;
            System.out.println("  FAIL  " + label);
        }
    }

    /**
     * Returns {@code true} if the runnable throws
     * {@link IllegalArgumentException}.
     */
    private static boolean expectIAE(Runnable r) {
        try {
            r.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    /**
     * Writes a string to a file in UTF-8.
     */
    private static void writeText(File file, String text) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(
                file.toPath(), StandardCharsets.UTF_8)) {
            w.write(text);
        }
    }
}

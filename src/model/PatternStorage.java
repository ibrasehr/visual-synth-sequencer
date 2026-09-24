package model;

import java.awt.Component;
import java.io.File;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Static helper class that wires {@link SequencerModel} persistence to Swing
 * file-chooser dialogs.
 *
 * <p>Member 4 (UI Engineer) can hook up toolbar buttons with a single call each:</p>
 * <pre>
 *   saveButton.addActionListener(e -&gt;
 *       PatternStorage.saveWithDialog(frame, model));
 *
 *   loadButton.addActionListener(e -&gt;
 *       PatternStorage.loadWithDialog(frame, model, () -&gt; gridPanel.repaint()));
 * </pre>
 *
 * @author Member 3 – Data &amp; Persistence Engineer
 * @see SequencerModel
 */
public final class PatternStorage {

    /** File filter shown in the file chooser. */
    private static final FileNameExtensionFilter JSON_FILTER =
        new FileNameExtensionFilter("Beat Pattern (*.json)", "json");

    /**
     * Remembers the last directory the user navigated to, so the next dialog
     * opens in the same place. Defaults to the user's home directory.
     */
    private static File lastDirectory = new File(System.getProperty("user.home"));

    /** Utility class – no instantiation. */
    private PatternStorage() { }

    /**
     * Opens a "Save" dialog and writes the model's current pattern to the
     * chosen file.
     *
     * <ul>
     *   <li>Appends {@code .json} if the user doesn't type it.</li>
     *   <li>Asks for confirmation before overwriting an existing file.</li>
     *   <li>Shows a success message on completion.</li>
     *   <li>Shows an error dialog (not a stack trace) if saving fails.</li>
     *   <li>Does nothing if the user cancels.</li>
     * </ul>
     *
     * @param parent the parent component for dialog centering (may be {@code null})
     * @param model  the sequencer model to save
     */
    public static void saveWithDialog(Component parent, SequencerModel model) {
        JFileChooser chooser = createChooser();
        int result = chooser.showSaveDialog(parent);
        if (result != JFileChooser.APPROVE_OPTION) {
            return; // user cancelled
        }

        lastDirectory = chooser.getCurrentDirectory();
        File file = chooser.getSelectedFile();

        // Auto-append .json
        if (!file.getName().toLowerCase().endsWith(".json")) {
            file = new File(file.getAbsolutePath() + ".json");
        }

        // Confirm overwrite
        if (file.exists()) {
            int confirm = JOptionPane.showConfirmDialog(
                parent,
                "\"" + file.getName() + "\" already exists.\nOverwrite?",
                "Confirm Overwrite",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }

        try {
            model.savePattern(file);
            JOptionPane.showMessageDialog(
                parent,
                "Pattern saved to \"" + file.getName() + "\".",
                "Save Successful",
                JOptionPane.INFORMATION_MESSAGE);
        } catch (PatternFileException e) {
            JOptionPane.showMessageDialog(
                parent,
                e.getMessage(),
                "Save Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Opens a "Load" dialog and reads a pattern from the chosen file into
     * the model.
     *
     * <p>If the load succeeds, the supplied {@code onLoaded} callback is
     * invoked so the UI can repaint the grid.</p>
     *
     * <ul>
     *   <li>Shows an error dialog (not a stack trace) if loading fails.</li>
     *   <li>Does nothing if the user cancels.</li>
     * </ul>
     *
     * @param parent   the parent component for dialog centering (may be {@code null})
     * @param model    the sequencer model to load into
     * @param onLoaded callback invoked after a successful load (e.g.
     *                 {@code () -> gridPanel.repaint()})
     */
    public static void loadWithDialog(Component parent, SequencerModel model,
                                      Runnable onLoaded) {
        JFileChooser chooser = createChooser();
        int result = chooser.showOpenDialog(parent);
        if (result != JFileChooser.APPROVE_OPTION) {
            return; // user cancelled
        }

        lastDirectory = chooser.getCurrentDirectory();
        File file = chooser.getSelectedFile();

        try {
            model.loadPattern(file);
            if (onLoaded != null) {
                onLoaded.run();
            }
        } catch (PatternFileException e) {
            JOptionPane.showMessageDialog(
                parent,
                e.getMessage(),
                "Load Error",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    // ------------------------------------------------------------------
    // Internal
    // ------------------------------------------------------------------

    /**
     * Creates a pre-configured {@link JFileChooser}.
     */
    private static JFileChooser createChooser() {
        JFileChooser chooser = new JFileChooser(lastDirectory);
        chooser.setFileFilter(JSON_FILTER);
        chooser.setAcceptAllFileFilterUsed(false);
        return chooser;
    }
}

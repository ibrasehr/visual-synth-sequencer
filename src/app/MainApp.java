package app;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;

import audio.AudioEngine;
import audio.BeatDetector;
import model.PatternStorage;
import model.SequencerModel;
import ui.GridPanel;
import ui.OscilloscopePanel;
import ui.UITheme;

public class MainApp extends JFrame {
    private static final long serialVersionUID = 1L;

    private final SequencerModel model;
    private final GridPanel gridPanel;
    private final OscilloscopePanel oscilloscopePanel;
    private JToolBar toolbar;
    private Timer timer;
    private JLabel bpmLabel;
    private JSlider bpmSlider;

    private int currentStep = -1;
    private int bpm = 120;
    private AudioEngine.Waveform currentWaveform = AudioEngine.Waveform.SINE;
    private JComboBox<String> presetChordDropdown;

    public MainApp() {
        setTitle("Visual Synth Sequencer — Executive Control Panel");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Initialize core model and visual panel
        model = new SequencerModel();
        gridPanel = new GridPanel(model);
        add(gridPanel, BorderLayout.CENTER);

        // Real-time Oscilloscope / Waveform Visualizer
        oscilloscopePanel = new OscilloscopePanel();
        add(oscilloscopePanel, BorderLayout.SOUTH);

        // Build top control toolbar
        toolbar = createToolBar();
        add(toolbar, BorderLayout.NORTH);

        applyTheme();
        UITheme.addThemeChangeListener(this::applyTheme);

        pack();
        setPreferredSize(new Dimension(1260, 620));
        setMinimumSize(new Dimension(1180, 580));
        setSize(1260, 620);
        setLocationRelativeTo(null);
        setResizable(true);

        initTimingEngine();
    }

    private void applyTheme() {
        getContentPane().setBackground(UITheme.getBackgroundDark());
        if (toolbar != null) {
            toolbar.setBackground(UITheme.getToolbarBackground());
        }
        repaint();
    }

    private JToolBar createToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 4));
        toolBar.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));

        // Transport Controls
        JButton playBtn = new JButton("▶ Play");
        JButton stopBtn = new JButton("⏹ Stop");

        playBtn.addActionListener(e -> startSequencer());
        stopBtn.addActionListener(e -> stopSequencer());

        // BPM Controls
        bpmLabel = new JLabel("BPM: " + bpm);
        bpmLabel.setPreferredSize(new Dimension(60, 20));
        bpmSlider = new JSlider(60, 240, bpm);
        bpmSlider.setPreferredSize(new Dimension(115, 24));
        bpmSlider.addChangeListener(e -> {
            bpm = bpmSlider.getValue();
            bpmLabel.setText("BPM: " + bpm);
            try {
                model.setBpm(bpm);
            } catch (IllegalArgumentException ignored) {
            }
            updateTimerSpeed();
        });

        // Waveform Selection Dropdown
        JLabel waveLabel = new JLabel("Waveform:");
        String[] waveformOptions = {"Sine", "Square", "Sawtooth", "Triangle"};
        JComboBox<String> waveCombo = new JComboBox<>(waveformOptions);
        waveCombo.setPreferredSize(new Dimension(95, 24));
        waveCombo.addActionListener(e -> {
            String selected = (String) waveCombo.getSelectedItem();
            if (selected != null) {
                switch (selected) {
                    case "Square":
                        currentWaveform = AudioEngine.Waveform.SQUARE;
                        break;
                    case "Sawtooth":
                        currentWaveform = AudioEngine.Waveform.SAWTOOTH;
                        break;
                    case "Triangle":
                        currentWaveform = AudioEngine.Waveform.TRIANGLE;
                        break;
                    case "Sine":
                    default:
                        currentWaveform = AudioEngine.Waveform.SINE;
                        break;
                }
            }
        });

        // Static Preset Chord / Pattern Dropdown
        String[] presetNames = {"Presets: None", "C-Major Arpeggio", "Basic Beat", "C-Major Chord"};
        presetChordDropdown = new JComboBox<>(presetNames);
        presetChordDropdown.setPreferredSize(new Dimension(125, 24));
        presetChordDropdown.setToolTipText("Select static chord or pattern preset");
        presetChordDropdown.addActionListener(e -> handlePresetSelection());

        // Save, Load, Reset, and Import Controls (Utility Buttons)
        JButton saveBtn = new JButton("Save Pattern");
        JButton loadBtn = new JButton("Load Pattern");
        JButton resetBtn = new JButton("Reset Grid");
        JButton importWavBtn = new JButton("Import WAV");
        importWavBtn.setToolTipText("Import Beat from WAV");

        saveBtn.addActionListener(e -> handleSavePattern());
        loadBtn.addActionListener(e -> handleLoadPattern());
        resetBtn.addActionListener(e -> {
            model.clear();
            AudioEngine.clearSongData();
            if (presetChordDropdown != null) {
                presetChordDropdown.setSelectedIndex(0);
            }
            gridPanel.repaint();
        });
        importWavBtn.addActionListener(e -> handleImportBeat());

        // Theme Switcher Group (Keeps label and dropdown atomically together)
        JPanel themeGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        themeGroup.setOpaque(false);
        JLabel themeLabel = new JLabel("Theme:");
        JComboBox<UITheme.ThemeMode> themeDropdown = new JComboBox<>(UITheme.ThemeMode.values());
        themeDropdown.setSelectedItem(UITheme.getCurrentTheme());
        themeDropdown.setPreferredSize(new Dimension(135, 24));
        themeDropdown.setToolTipText("Select Color Theme (Industrial Gothic / Neon Cyberpunk)");
        themeDropdown.addActionListener(e -> {
            UITheme.ThemeMode selected = (UITheme.ThemeMode) themeDropdown.getSelectedItem();
            if (selected != null) {
                UITheme.setTheme(selected);
            }
        });
        themeGroup.add(themeLabel);
        themeGroup.add(themeDropdown);

        // Assemble Controls into Toolbar
        toolBar.add(playBtn);
        toolBar.add(stopBtn);
        toolBar.add(Box.createHorizontalStrut(3));
        toolBar.addSeparator();
        toolBar.add(Box.createHorizontalStrut(3));
        toolBar.add(bpmLabel);
        toolBar.add(bpmSlider);
        toolBar.add(Box.createHorizontalStrut(3));
        toolBar.addSeparator();
        toolBar.add(Box.createHorizontalStrut(3));
        toolBar.add(waveLabel);
        toolBar.add(waveCombo);
        toolBar.add(Box.createHorizontalStrut(3));
        toolBar.addSeparator();
        toolBar.add(Box.createHorizontalStrut(3));
        toolBar.add(new JLabel("Preset:"));
        toolBar.add(presetChordDropdown);
        toolBar.add(Box.createHorizontalStrut(3));
        toolBar.addSeparator();
        toolBar.add(Box.createHorizontalStrut(3));
        toolBar.add(saveBtn);
        toolBar.add(loadBtn);
        toolBar.add(resetBtn);
        toolBar.add(importWavBtn);
        toolBar.add(Box.createHorizontalStrut(3));
        toolBar.addSeparator();
        toolBar.add(Box.createHorizontalStrut(3));
        toolBar.add(themeGroup);

        return toolBar;
    }

    private void initTimingEngine() {
        int delay = calculateDelay(bpm);
        timer = new Timer(delay, e -> advanceStep());
    }

    private void updateTimerSpeed() {
        if (timer != null) {
            timer.setDelay(calculateDelay(bpm));
        }
    }

    private int calculateDelay(int bpmValue) {
        // Calculates millisecond interval per 16th note step: (60,000ms / BPM) / 4
        return (60000 / bpmValue) / 4;
    }

    private void startSequencer() {
        if (!timer.isRunning()) {
            timer.start();
        }
    }

    private void stopSequencer() {
        if (timer.isRunning()) {
            timer.stop();
        }
        currentStep = -1;
        gridPanel.setPlayheadStep(currentStep);
        gridPanel.repaint();
    }

    private void advanceStep() {
        // Advance playhead step (0 to 15)
        currentStep = (currentStep + 1) % SequencerModel.COLS;
        gridPanel.setPlayheadStep(currentStep);
        gridPanel.repaint();

        boolean songActive = AudioEngine.isSongLoaded();
        boolean sampleTriggered = false;

        // Query active cells in current column and trigger audio
        for (int row = 0; row < SequencerModel.ROWS; row++) {
            if (model.isCellActive(row, currentStep)) {
                if (songActive && !sampleTriggered) {
                    // Play the audio slice once per step (avoid stacking duplicates)
                    AudioEngine.playSample(currentStep);
                    sampleTriggered = true;
                } else if (!songActive) {
                    // Fallback: synthesize tone using your multi-waveform dropdown selection
                    double frequency = model.getFrequency(row);
                    AudioEngine.playTone(frequency, 120, currentWaveform);
                }
            }
        }
    }

    private void handleSavePattern() {
        PatternStorage.saveWithDialog(this, model);
    }

    private void handleLoadPattern() {
        PatternStorage.loadWithDialog(this, model, () -> {
            gridPanel.repaint();
            bpm = model.getBpm();
            if (bpmSlider != null) {
                bpmSlider.setValue(bpm);
            }
            if (bpmLabel != null) {
                bpmLabel.setText("BPM: " + bpm);
            }
            updateTimerSpeed();
        });
    }

    private void handlePresetSelection() {
        if (presetChordDropdown == null) return;
        int index = presetChordDropdown.getSelectedIndex();
        if (index <= 0) return;

        // Revert audio engine to synthesized tones for presets
        AudioEngine.clearSongData();
        model.clear();
        switch (index) {
            case 1 -> { // C-Major Arpeggio
                model.setCell(0, 0, true);
                model.setCell(3, 2, true);
                model.setCell(5, 4, true);
                model.setCell(7, 6, true);
                model.setCell(0, 8, true);
                model.setCell(3, 10, true);
                model.setCell(5, 12, true);
                model.setCell(7, 14, true);
            }
            case 2 -> { // Basic Beat
                for (int c = 0; c < SequencerModel.COLS; c += 4) {
                    model.setCell(0, c, true);
                    model.setCell(7, c, true);
                }
                model.setCell(4, 2, true);
                model.setCell(4, 6, true);
                model.setCell(4, 10, true);
                model.setCell(4, 14, true);
            }
            case 3 -> { // C-Major Chord
                for (int c = 0; c < SequencerModel.COLS; c += 4) {
                    model.setCell(0, c, true);
                    model.setCell(3, c, true);
                    model.setCell(5, c, true);
                    model.setCell(7, c, true);
                }
            }
        }
        gridPanel.repaint();
    }

    private void handleImportBeat() {
        try {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Import Custom WAV Beat");
            FileNameExtensionFilter filter = new FileNameExtensionFilter("WAV Audio (*.wav)", "wav");
            chooser.setFileFilter(filter);
            chooser.setAcceptAllFileFilterUsed(false);

            int result = chooser.showOpenDialog(this);
            if (result != JFileChooser.APPROVE_OPTION) {
                return;
            }

            File selectedFile = chooser.getSelectedFile();
            if (selectedFile == null) {
                return;
            }

            // 1. Bypass & ignore any selected item in the static preset chord dropdown
            if (presetChordDropdown != null) {
                presetChordDropdown.setSelectedIndex(0);
            }

            // 2. Dynamically extract 16-step beat energy from the custom WAV file
            boolean[] detectedBeats = BeatDetector.analyzeAudio(selectedFile);
            if (detectedBeats == null || detectedBeats.length < SequencerModel.COLS) {
                return;
            }

            // 3. Load the song's PCM data into AudioEngine for slice playback
            try {
                AudioEngine.loadSongData(selectedFile);
            } catch (IOException | javax.sound.sampled.UnsupportedAudioFileException ex) {
                JOptionPane.showMessageDialog(
                    this,
                    "Beat grid loaded but audio playback could not be prepared:\n" + ex.getMessage(),
                    "Audio Load Warning",
                    JOptionPane.WARNING_MESSAGE
                );
                // Beat grid still works; synth fallback will be used
            }

            // 4. Consume newly extracted 16-step boolean array into sequencer model
            model.loadBeatPattern(detectedBeats);
            gridPanel.repaint();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                this,
                "Failed to import beat from audio file: " + e.getMessage(),
                "Import Error",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainApp().setVisible(true));
    }
}
package ui;

import javax.swing.*;
import java.awt.*;
import model.SequencerModel;

/**
 * STUB — belongs to Member 2 (UI & Visuals Engineer).
 * This file exists only so MainApp compiles and runs on branch feature/integration
 * while Member 2 finishes their real implementation on feature/ui.
 * Replace this file entirely with Member 2's version before final merge —
 * do not edit the constructor or setPlayheadStep signature, MainApp depends on them exactly as-is.
 */
public class GridPanel extends JPanel {
    private final SequencerModel model;
    private int playheadStep = -1;

    public GridPanel(SequencerModel model) {
        this.model = model;
        setPreferredSize(new Dimension(800, 400));
        setBackground(Color.DARK_GRAY);
    }

    public void setPlayheadStep(int step) {
        this.playheadStep = step;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        // Member 2 implementation: Graphics2D matrix rendering & mouse click listeners
        g.setColor(Color.LIGHT_GRAY);
        g.drawString("GridPanel stub — waiting on Member 2 (step=" + playheadStep + ")", 20, 20);
    }
}

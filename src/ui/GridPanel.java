package ui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;
import model.SequencerModel;

/**
 * Custom-rendered matrix grid component displaying 16 steps by 8 pitch rows.
 * Features dynamic playhead animations and real-time palette updates via UITheme.
 */
public class GridPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final SequencerModel model;
    private int playheadStep = -1;

    private static final String[] ROW_LABELS = {"C5", "B4", "A4", "G4", "F4", "E4", "D4", "C4"};
    private static final int START_X = 50;
    private static final int START_Y = 40;

    public GridPanel(SequencerModel model) {
        this.model = model;
        
        int preferredWidth = START_X + UITheme.STEPS * (UITheme.CELL_SIZE + UITheme.GRID_GAP) + 30;
        int preferredHeight = START_Y + UITheme.ROWS * (UITheme.CELL_SIZE + UITheme.GRID_GAP) + 30;
        setPreferredSize(new Dimension(Math.max(800, preferredWidth), Math.max(400, preferredHeight)));
        setBackground(UITheme.getBackgroundDark());

        // Mouse click listener for matrix cell interaction
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleGridClick(e.getX(), e.getY());
            }
        });

        // Repaint immediately on theme change
        UITheme.addThemeChangeListener(() -> {
            setBackground(UITheme.getBackgroundDark());
            repaint();
        });
    }

    public void setPlayheadStep(int step) {
        this.playheadStep = step;
        repaint();
    }

    public int getPlayheadStep() {
        return playheadStep;
    }

    private void handleGridClick(int mouseX, int mouseY) {
        for (int row = 0; row < UITheme.ROWS; row++) {
            for (int col = 0; col < UITheme.STEPS; col++) {
                int cellX = START_X + col * (UITheme.CELL_SIZE + UITheme.GRID_GAP);
                int cellY = START_Y + row * (UITheme.CELL_SIZE + UITheme.GRID_GAP);

                if (mouseX >= cellX && mouseX <= cellX + UITheme.CELL_SIZE &&
                    mouseY >= cellY && mouseY <= cellY + UITheme.CELL_SIZE) {
                    
                    if (model != null) {
                        model.toggleCell(row, col);
                    }
                    repaint();
                    return;
                }
            }
        }
    }

    private boolean isCellActive(int row, int col) {
        return model != null && model.isCellActive(row, col);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int gridW = UITheme.STEPS * (UITheme.CELL_SIZE + UITheme.GRID_GAP) - UITheme.GRID_GAP + 24;
        int gridH = UITheme.ROWS * (UITheme.CELL_SIZE + UITheme.GRID_GAP) - UITheme.GRID_GAP + 24;

        // Draw Matrix Container Panel
        g2d.setColor(UITheme.getPanelBackground());
        g2d.fillRoundRect(START_X - 12, START_Y - 12, gridW, gridH, 15, 15);
        g2d.setColor(UITheme.getBorderColor());
        g2d.setStroke(new BasicStroke(1f));
        g2d.drawRoundRect(START_X - 12, START_Y - 12, gridW, gridH, 15, 15);

        // Draw Dynamic Playhead Highlight if active (Steps 1 to 16)
        if (playheadStep >= 0 && playheadStep < UITheme.STEPS) {
            int pX = START_X + playheadStep * (UITheme.CELL_SIZE + UITheme.GRID_GAP) - UITheme.GRID_GAP / 2;
            int pW = UITheme.CELL_SIZE + UITheme.GRID_GAP;
            int pH = gridH - 12;

            // Glowing translucent column beam
            g2d.setColor(UITheme.getPlayheadBeam());
            g2d.fillRoundRect(pX, START_Y - 6, pW, pH, 10, 10);

            // Shifting glowing border
            g2d.setColor(UITheme.getPlayheadBorder());
            g2d.setStroke(new BasicStroke(1.5f));
            g2d.drawRoundRect(pX, START_Y - 6, pW, pH, 10, 10);
        }

        // Draw Column Step Numbers (1 to 16)
        FontMetrics fmCol = g2d.getFontMetrics();
        for (int col = 0; col < UITheme.STEPS; col++) {
            String stepStr = String.valueOf(col + 1);
            int cx = START_X + col * (UITheme.CELL_SIZE + UITheme.GRID_GAP) + UITheme.CELL_SIZE / 2;
            int nx = cx - fmCol.stringWidth(stepStr) / 2;
            
            if (col == playheadStep) {
                g2d.setFont(UITheme.NORMAL_FONT);
                g2d.setColor(UITheme.getPrimaryAccent());
            } else {
                g2d.setFont(UITheme.SMALL_FONT);
                g2d.setColor(UITheme.getTextMuted());
            }
            g2d.drawString(stepStr, nx, START_Y - 20);
        }

        // Draw Matrix Grid & Row Note Labels
        for (int row = 0; row < UITheme.ROWS; row++) {
            // Note Label (e.g. C5, B4, ...)
            g2d.setColor(UITheme.getTextLight());
            g2d.setFont(UITheme.NORMAL_FONT);
            FontMetrics fmRow = g2d.getFontMetrics();
            String label = row < ROW_LABELS.length ? ROW_LABELS[row] : "R" + (row + 1);
            int lx = START_X - 25 - fmRow.stringWidth(label) / 2;
            int ly = START_Y + row * (UITheme.CELL_SIZE + UITheme.GRID_GAP) + UITheme.CELL_SIZE / 2 + fmRow.getAscent() / 2 - 2;
            g2d.drawString(label, lx, ly);

            // Row Cells
            for (int col = 0; col < UITheme.STEPS; col++) {
                int x = START_X + col * (UITheme.CELL_SIZE + UITheme.GRID_GAP);
                int y = START_Y + row * (UITheme.CELL_SIZE + UITheme.GRID_GAP);

                boolean active = isCellActive(row, col);

                if (active) {
                    boolean isHit = (col == playheadStep);
                    // Outer glow
                    g2d.setColor(isHit ? UITheme.getPlayheadHitGlow() : UITheme.getActiveCellGlow());
                    g2d.fillRoundRect(x - 2, y - 2, UITheme.CELL_SIZE + 4, UITheme.CELL_SIZE + 4, 12, 12);
                    // Inner active fill
                    g2d.setColor(isHit ? Color.WHITE : UITheme.getActiveCellFill());
                } else {
                    g2d.setColor(UITheme.getCellInactive());
                }

                g2d.fillRoundRect(x, y, UITheme.CELL_SIZE, UITheme.CELL_SIZE, 8, 8);
            }
        }
    }
}

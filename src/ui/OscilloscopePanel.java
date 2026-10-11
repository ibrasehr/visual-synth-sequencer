package ui;

import audio.AudioEngine;
import java.awt.*;
import java.awt.geom.Path2D;
import javax.swing.*;

/**
 * Dedicated real-time oscilloscope visualizer component using Graphics2D.
 * Dynamically queries AudioEngine mixer buffer snapshots and renders frequency/amplitude traces.
 */
public class OscilloscopePanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private static final int BUFFER_SIZE = 256;
    private final float[] waveData = new float[BUFFER_SIZE];
    private final Timer animTimer;

    // Phase counter for ambient idle wave animation when audio is silent
    private float idlePhase = 0f;

    public OscilloscopePanel() {
        setPreferredSize(new Dimension(800, 75));
        setOpaque(false);

        // Update visualizer at ~35 FPS (28 ms interval)
        animTimer = new Timer(28, e -> {
            AudioEngine.getWaveformSnapshot(waveData);
            idlePhase += 0.08f;
            repaint();
        });
        animTimer.start();

        // Repaint immediately on theme change
        UITheme.addThemeChangeListener(this::repaint);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth() - 24;
        int h = getHeight() - 16;
        int x0 = 12;
        int y0 = 8;
        int midY = y0 + h / 2;

        // Container panel background
        g2d.setColor(UITheme.getPanelBackground());
        g2d.fillRoundRect(x0, y0, w, h, 12, 12);

        // Container border
        g2d.setColor(UITheme.getBorderColor());
        g2d.setStroke(new BasicStroke(1f));
        g2d.drawRoundRect(x0, y0, w, h, 12, 12);

        // Grid lines / Center baseline
        Color borderColor = UITheme.getBorderColor();
        g2d.setColor(new Color(borderColor.getRed(), borderColor.getGreen(), borderColor.getBlue(), 45));
        g2d.drawLine(x0 + 10, midY, x0 + w - 10, midY);
        for (int gx = x0 + 60; gx < x0 + w - 30; gx += 70) {
            g2d.drawLine(gx, y0 + 6, gx, y0 + h - 6);
        }

        // Header label
        g2d.setFont(UITheme.SMALL_FONT);
        g2d.setColor(UITheme.getTextMuted());
        g2d.drawString("REAL-TIME OSCILLOSCOPE // DSP MONITOR", x0 + 16, y0 + 16);

        // Peak signal detection
        float maxAmp = 0f;
        for (float v : waveData) {
            float abs = Math.abs(v);
            if (abs > maxAmp) maxAmp = abs;
        }

        Path2D.Float path = new Path2D.Float();
        int plotW = w - 40;
        int plotX = x0 + 20;
        float maxH = (h / 2f) - 14;

        boolean isSilent = (maxAmp < 0.005f);

        for (int i = 0; i < BUFFER_SIZE; i++) {
            float px = plotX + (float) i / (BUFFER_SIZE - 1) * plotW;
            float sample;
            if (isSilent) {
                // Gentle idle wave ripple when audio is paused
                sample = (float) Math.sin(i * 0.12f + idlePhase) * 0.035f;
            } else {
                sample = waveData[i];
            }
            float py = midY - (sample * maxH);

            if (i == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
        }

        Color waveColor = UITheme.getOscilloscopeWave();

        // 1. Glowing blur/halo pass underneath
        g2d.setColor(new Color(waveColor.getRed(), waveColor.getGreen(), waveColor.getBlue(), isSilent ? 35 : 85));
        g2d.setStroke(new BasicStroke(isSilent ? 2f : 4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.draw(path);

        // 2. Sharp foreground waveform trace
        g2d.setColor(isSilent ? UITheme.getTextMuted() : waveColor);
        g2d.setStroke(new BasicStroke(isSilent ? 1f : 1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2d.draw(path);
    }
}

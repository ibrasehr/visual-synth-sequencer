package ui;

import java.awt.Color;
import java.awt.Font;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Global UI theme definitions, dimensions, fonts, and dynamic color palettes.
 * Supports switching dynamically between Industrial Gothic and Neon Cyberpunk themes.
 */
public final class UITheme {

    private UITheme() {
        // Prevent creating objects from this utility class
    }

    public static final int ROWS = 8;
    public static final int STEPS = 16;

    public static final int CELL_SIZE = 45;
    public static final int GRID_GAP = 5;

    public static final Font TITLE_FONT =
            new Font("SansSerif", Font.BOLD, 24);

    public static final Font NORMAL_FONT =
            new Font("SansSerif", Font.PLAIN, 14);

    public static final Font SMALL_FONT =
            new Font("SansSerif", Font.PLAIN, 12);

    public enum ThemeMode {
        INDUSTRIAL_GOTHIC("Industrial Gothic"),
        NEON_CYBERPUNK("Neon Cyberpunk");

        private final String displayName;

        ThemeMode(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    // Active theme state and change listeners
    private static volatile ThemeMode currentTheme = ThemeMode.INDUSTRIAL_GOTHIC;
    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public static ThemeMode getCurrentTheme() {
        return currentTheme;
    }

    public static void setTheme(ThemeMode theme) {
        if (theme != null && theme != currentTheme) {
            currentTheme = theme;
            for (Runnable listener : listeners) {
                try {
                    listener.run();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static void addThemeChangeListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    // ---- Dynamic Palette Tokens ------------------------------------------

    /** Main window and background */
    public static Color getBackgroundDark() {
        return currentTheme == ThemeMode.INDUSTRIAL_GOTHIC
            ? new Color(0x0C, 0x0A, 0x10) // Deep dark purple #0C0A10
            : new Color(0x05, 0x05, 0x08); // Pitch black #050508
    }

    /** Panels / Grid Container background */
    public static Color getPanelBackground() {
        return currentTheme == ThemeMode.INDUSTRIAL_GOTHIC
            ? new Color(0x1A, 0x14, 0x20) // Deep container purple #1A1420
            : new Color(0x0D, 0x0F, 0x18); // Cyberpunk container #0D0F18
    }

    /** Top Toolbar background */
    public static Color getToolbarBackground() {
        return currentTheme == ThemeMode.INDUSTRIAL_GOTHIC
            ? new Color(0x15, 0x10, 0x1D) // Dark purple toolbar
            : new Color(0x08, 0x0A, 0x12); // Pitch black / dark navy toolbar
    }

    /** Inactive step cell fill */
    public static Color getCellInactive() {
        return currentTheme == ThemeMode.INDUSTRIAL_GOTHIC
            ? new Color(0x30, 0x19, 0x34) // Deep dark purple cell #301934
            : new Color(0x14, 0x18, 0x24); // Dark cyber navy #141824
    }

    /** Container borders */
    public static Color getBorderColor() {
        return currentTheme == ThemeMode.INDUSTRIAL_GOTHIC
            ? new Color(0x4A, 0x28, 0x6D) // Muted gothic purple
            : new Color(0x00, 0x88, 0x9B); // Subtle neon cyan border
    }

    /** Primary theme accent color */
    public static Color getPrimaryAccent() {
        return currentTheme == ThemeMode.INDUSTRIAL_GOTHIC
            ? new Color(0x6B, 0x2F, 0xA6) // Accent violet #6B2FA6
            : new Color(0x00, 0xF0, 0xFF); // Bright neon cyan #00F0FF
    }

    /** Secondary theme accent color */
    public static Color getSecondaryAccent() {
        return currentTheme == ThemeMode.INDUSTRIAL_GOTHIC
            ? new Color(0x9B, 0x4D, 0xCA) // Bright violet
            : new Color(0xFF, 0x00, 0x7F); // Hot pink #FF007F
    }

    /** Active note cell fill */
    public static Color getActiveCellFill() {
        return currentTheme == ThemeMode.INDUSTRIAL_GOTHIC
            ? new Color(0xBA, 0x68, 0xC8) // Bright violet fill
            : new Color(0xFF, 0x00, 0x7F); // Hot pink fill #FF007F
    }

    /** Active note cell outer glow */
    public static Color getActiveCellGlow() {
        return currentTheme == ThemeMode.INDUSTRIAL_GOTHIC
            ? new Color(0x6B, 0x2F, 0xA6) // Violet glow #6B2FA6
            : new Color(0x00, 0xF0, 0xFF); // Neon cyan glow #00F0FF
    }

    /** Dynamic playhead column background tint */
    public static Color getPlayheadBeam() {
        return currentTheme == ThemeMode.INDUSTRIAL_GOTHIC
            ? new Color(107, 47, 166, 60)  // Translucent violet beam
            : new Color(0, 240, 255, 65);   // Translucent neon cyan beam
    }

    /** Dynamic playhead column border */
    public static Color getPlayheadBorder() {
        return currentTheme == ThemeMode.INDUSTRIAL_GOTHIC
            ? new Color(186, 104, 200, 190) // Glowing purple border
            : new Color(255, 0, 127, 210);  // Glowing hot pink border
    }

    /** Note cell hit highlight when playhead crosses active cell */
    public static Color getPlayheadHitGlow() {
        return currentTheme == ThemeMode.INDUSTRIAL_GOTHIC
            ? new Color(230, 190, 255)
            : new Color(255, 255, 255);
    }

    /** Text primary */
    public static Color getTextLight() {
        return currentTheme == ThemeMode.INDUSTRIAL_GOTHIC
            ? new Color(0xE0, 0xD0, 0xF0)
            : new Color(0x00, 0xF0, 0xFF);
    }

    /** Text secondary / muted */
    public static Color getTextMuted() {
        return currentTheme == ThemeMode.INDUSTRIAL_GOTHIC
            ? new Color(0x8E, 0x7C, 0x9E)
            : new Color(0x65, 0x85, 0xA5);
    }

    /** Oscilloscope waveform color */
    public static Color getOscilloscopeWave() {
        return currentTheme == ThemeMode.INDUSTRIAL_GOTHIC
            ? new Color(0xBA, 0x68, 0xC8) // Industrial violet trace
            : new Color(0x00, 0xF0, 0xFF); // Neon cyan trace #00F0FF
    }
}

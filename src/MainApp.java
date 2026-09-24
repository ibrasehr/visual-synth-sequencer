import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class MainApp extends JFrame {
    public MainApp() {
        setTitle("Visual Synth Sequencer");
        setSize(1000, 550);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainApp().setVisible(true));
    }
}
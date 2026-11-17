package id.peternakan.desktop;

import com.formdev.flatlaf.FlatLightLaf;
import id.peternakan.desktop.api.ApiClient;
import id.peternakan.desktop.ui.LoginFrame;
import id.peternakan.desktop.ui.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.InputMap;
import javax.swing.KeyStroke;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.text.DefaultEditorKit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Aplikasi klien Swing. Layanan (API) harus sudah berjalan; alamat layanan
 * diambil dari variabel lingkungan API_BASE_URL.
 */
public final class DesktopApp {
    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(new FlatLightLaf());
                UIManager.put("Component.focusWidth", 1);
                UIManager.put("Button.arc", 10);
                UIManager.put("Component.arc", 10);
                UIManager.put("TextComponent.arc", 10);
            } catch (Exception e) {
                System.err.println("LookAndFeel gagal: " + e.getMessage());
            }
            installPasteShortcuts(UIManager.getDefaults());
            showLogin();
        });
    }

    /** Izinkan shortcut paste Windows/Linux dan macOS pada seluruh komponen teks. */
    static void installPasteShortcuts(UIDefaults defaults) {
        String[] inputMapKeys = {
                "TextField.focusInputMap",
                "PasswordField.focusInputMap",
                "FormattedTextField.focusInputMap",
                "TextArea.focusInputMap",
                "TextPane.focusInputMap",
                "EditorPane.focusInputMap"
        };
        for (String key : inputMapKeys) {
            Object value = defaults.get(key);
            if (value instanceof InputMap inputMap) {
                inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK),
                        DefaultEditorKit.pasteAction);
                inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.META_DOWN_MASK),
                        DefaultEditorKit.pasteAction);
            }
        }
    }

    public static void showLogin() {
        new LoginFrame(ApiClient.fromEnvironment()).setVisible(true);
    }

    public static void showMain(ApiClient client) {
        new MainFrame(client).setVisible(true);
    }

    private DesktopApp() {
    }
}

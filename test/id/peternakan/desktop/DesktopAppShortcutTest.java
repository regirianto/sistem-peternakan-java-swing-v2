package id.peternakan.desktop;

import org.junit.jupiter.api.Test;

import javax.swing.InputMap;
import javax.swing.KeyStroke;
import javax.swing.UIDefaults;
import javax.swing.text.DefaultEditorKit;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DesktopAppShortcutTest {
    @Test
    void installsCtrlAndCommandPasteForEveryTextComponentType() {
        UIDefaults defaults = new UIDefaults();
        String[] keys = {
                "TextField.focusInputMap",
                "PasswordField.focusInputMap",
                "FormattedTextField.focusInputMap",
                "TextArea.focusInputMap",
                "TextPane.focusInputMap",
                "EditorPane.focusInputMap"
        };
        for (String key : keys) {
            defaults.put(key, new InputMap());
        }

        DesktopApp.installPasteShortcuts(defaults);

        KeyStroke ctrlV = KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK);
        KeyStroke commandV = KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.META_DOWN_MASK);
        for (String key : keys) {
            InputMap inputMap = (InputMap) defaults.get(key);
            assertEquals(DefaultEditorKit.pasteAction, inputMap.get(ctrlV), key);
            assertEquals(DefaultEditorKit.pasteAction, inputMap.get(commandV), key);
        }
    }
}

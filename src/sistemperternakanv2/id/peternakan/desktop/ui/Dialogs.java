package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;
import id.peternakan.desktop.api.ApiClientException;

import java.awt.Component;
import java.util.Map;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/** Dialog util: konfirmasi OK+Cancel, formulir, dan ganti password. */
public final class Dialogs {
    private Dialogs() {
    }

    /** Tampilkan dialog OK/CANCEL; true bila pengguna menekan OK. */
    public static boolean ok(Component parent, String title, Object... contents) {
        return JOptionPane.showConfirmDialog(parent, contents, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
    }

    public static void changePassword(Component parent, ApiClient client) {
        javax.swing.JPasswordField current = new javax.swing.JPasswordField();
        javax.swing.JPasswordField fresh = new javax.swing.JPasswordField();
        javax.swing.JPasswordField repeat = new javax.swing.JPasswordField();
        JPanel form = new JPanel();
        form.setLayout(new java.awt.GridBagLayout());
        java.awt.GridBagConstraints g = new java.awt.GridBagConstraints();
        g.insets = new java.awt.Insets(4, 2, 4, 8);
        g.gridx = 0;
        g.gridy = 0;
        form.add(new javax.swing.JLabel("Password saat ini"), g);
        g.gridy = 1;
        form.add(new javax.swing.JLabel("Password baru (min. 10 karakter)"), g);
        g.gridy = 2;
        form.add(new javax.swing.JLabel("Ulangi password baru"), g);
        g.gridx = 1;
        g.fill = java.awt.GridBagConstraints.HORIZONTAL;
        g.weightx = 1;
        g.gridy = 0;
        form.add(current, g);
        g.gridy = 1;
        form.add(fresh, g);
        g.gridy = 2;
        form.add(repeat, g);
        while (true) {
            int option = JOptionPane.showConfirmDialog(parent, form, "Ganti Password",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (option != JOptionPane.OK_OPTION) {
                return;
            }
            String next = String.valueOf(fresh.getPassword());
            if (next.length() < 10 || !next.equals(String.valueOf(repeat.getPassword()))) {
                Ui.error(parent, "Password baru minimal 10 karakter dan sama dengan pengulangan.");
                continue;
            }
            try {
                client.changePassword(String.valueOf(current.getPassword()), next);
                Ui.info(parent, "Password berhasil diubah.");
                return;
            } catch (ApiClientException e) {
                Ui.error(parent, e);
            }
        }
    }

    public static void show(Component parent, String title, Component content) {
        JOptionPane.showMessageDialog(parent, content, title, JOptionPane.PLAIN_MESSAGE);
    }
}

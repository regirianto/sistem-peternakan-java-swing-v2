package id.peternakan.desktop.ui;

import id.peternakan.desktop.DesktopApp;
import id.peternakan.desktop.api.ApiClient;
import id.peternakan.desktop.api.ApiClientException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Map;

/**
 * Layar masuk. Jika layanan belum memiliki owner (database baru), dialog
 * bootstrap ditampilkan terlebih dahulu.
 */
public final class LoginFrame extends JFrame {
    private final ApiClient client;
    private final JTextField username = new JTextField(18);
    private final JPasswordField password = new JPasswordField(18);
    private JButton loginButton;

    public LoginFrame(ApiClient client) {
        this.client = client;
        setTitle("Sistem Manajemen Peternakan Kambing");
        username.setName("login.username");
        password.setName("login.password");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(480, 360);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);

        JPanel brand = new JPanel(new GridBagLayout());
        brand.setBackground(Ui.GREEN);
        brand.setPreferredSize(new java.awt.Dimension(0, 90));
        JLabel name = new JLabel("Peternakan Kambing");
        name.setFont(name.getFont().deriveFont(Font.BOLD, 20f));
        name.setForeground(Color.WHITE);
        JLabel tag = new JLabel("Sistem Manajemen Ternak - Layanan Internal");
        tag.setForeground(new Color(0xBB, 0xF7, 0xD0));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        brand.add(name, c);
        c.gridy = 1;
        brand.add(tag, c);
        root.add(brand, BorderLayout.NORTH);

        root.add(formPanel(), BorderLayout.CENTER);
        root.add(actionPanel(), BorderLayout.SOUTH);

        Ui.async(this, "Memuat profil usaha", () -> client.get("/api/public/info", Map.of()), info -> {
            String company = ApiClient.str(info.get("companyName"));
            if (!company.isBlank()) {
                name.setText(company);
                setTitle(company);
            }
        });
        Ui.async(this, "Memeriksa layanan", client::bootstrapStatus, status -> {
            if (Boolean.TRUE.equals(status.get("needsBootstrap"))) {
                bootstrapOwner();
            }
        });
        setContentPane(root);
    }

    private JComponent formPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 28, 2, 28);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.NONE;
        g.gridx = 0;
        g.gridy = 0;
        panel.add(new JLabel("Username"), g);
        g.gridy = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        panel.add(username, g);
        g.gridy = 2;
        g.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Password"), g);
        g.gridy = 3;
        g.fill = GridBagConstraints.HORIZONTAL;
        panel.add(password, g);
        g.gridy = 4;
        JLabel hint = new JLabel("Hubungi owner untuk membuat akun Anda.");
        hint.setForeground(Ui.MUTED);
        hint.setFont(hint.getFont().deriveFont(11f));
        panel.add(hint, g);
        return panel;
    }

    private JComponent actionPanel() {
        loginButton = Ui.primary("Masuk", this::doLogin);
        loginButton.setName("login.submit");
        getRootPane().setDefaultButton(loginButton);
        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        actions.setOpaque(false);
        actions.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 16));
        actions.add(loginButton);
        return actions;
    }

    private void doLogin() {
        if (String.valueOf(password.getPassword()).isBlank() || username.getText().isBlank()) {
            Ui.error(this, "Username dan password wajib diisi.");
            return;
        }
        loginButton.setEnabled(false);
        Ui.async(this, "Masuk", () ->
                        client.login(username.getText().trim(), String.valueOf(password.getPassword())),
                result -> afterLogin(),
                () -> loginButton.setEnabled(true));
    }

    private void afterLogin() {
        Ui.async(this, "Memuat sesi", client::me, me -> {
            if (client.mustChangePassword()) {
                forcePasswordChange(this::openMain);
                return;
            }
            openMain();
        });
    }

    private void forcePasswordChange(Runnable next) {
        JPasswordField current = new JPasswordField();
        JPasswordField fresh = new JPasswordField();
        JPasswordField repeat = new JPasswordField();
        Object[] message = {
                "Password saat ini", current,
                "Password baru (min. 10 karakter)", fresh,
                "Ulangi password baru", repeat
        };
        int option = JOptionPane.showConfirmDialog(this, message, "Ganti Password Wajib",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option != JOptionPane.OK_OPTION) {
            return;
        }
        String newText = String.valueOf(fresh.getPassword());
        if (!newText.equals(String.valueOf(repeat.getPassword())) || newText.length() < 10) {
            Ui.error(this, "Password baru minimal 10 karakter dan sama dengan pengulangan.");
            forcePasswordChange(next);
            return;
        }
        Ui.async(this, "Mengganti password",
                () -> client.changePassword(String.valueOf(current.getPassword()), newText),
                ok -> next.run());
    }

    private void bootstrapOwner() {
        bootstrapDialog();
    }

    private void bootstrapDialog() {
        JTextField owner = new JTextField();
        JPasswordField pass = new JPasswordField();
        JPasswordField repeat = new JPasswordField();
        JTextField name = new JTextField();
        Object[] message = {
                "Database baru terdeteksi. Buat akun owner pertama.",
                "Username owner", owner,
                "Password (min. 10 karakter)", pass,
                "Ulangi password", repeat,
                "Nama lengkap", name
        };
        while (true) {
            int option = JOptionPane.showConfirmDialog(this, message, "Bootstrap Owner",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (option != JOptionPane.OK_OPTION) {
                System.exit(0);
            }
            String newPassword = String.valueOf(pass.getPassword());
            if (newPassword.length() < 10 || !newPassword.equals(String.valueOf(repeat.getPassword()))) {
                Ui.error(this, "Password minimal 10 karakter dan sama dengan pengulangan.");
                continue;
            }
            if (owner.getText().isBlank() || name.getText().isBlank()) {
                Ui.error(this, "Username dan nama lengkap wajib diisi.");
                continue;
            }
            try {
                client.bootstrapOwner(owner.getText().trim(), newPassword, name.getText().trim());
                username.setText(owner.getText());
                Ui.info(this, "Owner dibuat. Silakan masuk.");
                return;
            } catch (ApiClientException e) {
                Ui.error(this, e);
            }
        }
    }

    private void openMain() {
        DesktopApp.showMain(client);
        dispose();
    }
}

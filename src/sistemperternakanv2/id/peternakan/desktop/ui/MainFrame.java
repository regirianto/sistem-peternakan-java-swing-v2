package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;
import id.peternakan.desktop.DesktopApp;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

/**
 * Kerangka utama: sidebar navigasi dan isi bertukar lewat CardLayout.
 * Menu owner-only disembunyikan untuk role non-owner; layanan tetap memeriksa
 * izin pada setiap permintaan sehingga penyembunyian bukan satu-satunya kontrol.
 */
public final class MainFrame extends JFrame {

    public interface Refreshable {
        void refresh();
    }

    private static final Color SIDEBAR = new Color(0x0E, 0x46, 0x27);
    private static final Color SIDEBAR_ACTIVE = new Color(0x16, 0x65, 0x34);

    private final Map<String, String> titles = new LinkedHashMap<>();
    private final Map<String, JPanel> cards = new LinkedHashMap<>();
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel content = new JPanel(cardLayout);
    private final JPanel menuPanel = new JPanel();
    private final JComboBox<String> branchCombo = new JComboBox<>();
    private List<Map<String, Object>> branchRows = List.of();
    private boolean loadingBranches;
    private String active;

    public MainFrame(ApiClient client) {
        setTitle("Sistem Manajemen Peternakan Kambing - " + client.fullName());
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setSize(1240, 780);
        setLocationRelativeTo(null);
        registerPanels(client);
        buildLayout(client);
        Ui.async(this, "Memuat profil usaha", () -> client.get("/api/public/info", Map.of()), info -> {
            String company = ApiClient.str(info.get("companyName"));
            if (!company.isBlank()) {
                setTitle(company + " - " + client.fullName());
            }
        });
        loadBranches(client);
    }

    private void loadBranches(ApiClient client) {
        Ui.async(this, "Memuat cabang", () -> client.get("/api/branches", Map.of()).get("items"), items -> {
            branchRows = ApiClient.list(items);
            loadingBranches = true;
            branchCombo.removeAllItems();
            if (client.owner()) {
                branchCombo.addItem("Semua Cabang");
            }
            for (Map<String, Object> branch : branchRows) {
                branchCombo.addItem(ApiClient.str(branch.get("code")) + " - "
                        + ApiClient.str(branch.get("name")));
            }
            if (branchCombo.getItemCount() == 0) {
                branchCombo.addItem("Belum ada cabang");
                branchCombo.setEnabled(false);
            } else if (!client.owner()) {
                branchCombo.setSelectedIndex(0);
                client.activeBranchId(ApiClient.lng(branchRows.get(0).get("id")));
                branchCombo.setEnabled(branchCombo.getItemCount() > 1);
            } else {
                branchCombo.setSelectedIndex(0);
                client.activeBranchId(null);
            }
            loadingBranches = false;
        });
    }

    private void registerPanels(ApiClient client) {
        register("dashboard", "Dashboard", client.can("dashboard.view"), new DashboardPanel(client, this));
        register("goats", "Data Kambing", client.can("goat.read"), new GoatsPanel(client, this));
        register("pens", "Kandang", client.can("pen.manage"), new PensPanel(client, this));
        register("parties", "Supplier & Pelanggan",
                client.can("supplier.manage") || client.can("customer.manage"),
                new PartiesPanel(client, this));
        register("purchases", "Pembelian & Penerimaan", client.can("purchase.manage"),
                new PurchasesPanel(client, this));
        register("sales", "Penjualan", client.can("sale.manage"), new SalesPanel(client, this));
        register("slaughter", "Pemotongan",
                client.can("slaughter.manage") || client.can("meat.view"), new SlaughterPanel(client, this));
        register("meat", "Persediaan Daging", client.can("meat.view"), new MeatStockPanel(client, this));
        register("payments", "Pembayaran", client.can("payment.record"), new PaymentsPanel(client, this));
        register("feed", "Pakan", client.can("feed.record") || client.can("feed.manage"),
                new FeedPanel(client, this));
        register("expenses", "Pengeluaran & Alokasi", client.can("expense.manage"),
                new ExpensesPanel(client, this));
        register("reports", "Laporan", client.can("finance.view"), new ReportsPanel(client, this));
        if (client.owner()) {
            register("branches", "Cabang", client.can("branch.manage"), new BranchesPanel(client, this));
            register("users", "Pengguna", client.can("user.manage") && client.owner(),
                    new UsersPanel(client, this));
            register("audit", "Log Aktivitas", client.can("audit.read") && client.owner(),
                    new AuditPanel(client, this));
            register("settings", "Pengaturan & Periode", client.can("settings.manage") && client.owner(),
                    new SettingsPanel(client, this));
        }
    }

    private void register(String key, String title, boolean shown, JPanel panel) {
        if (shown) {
            titles.put(key, title);
            cards.put(key, panel);
            content.add(panel, key);
        }
    }

    private void buildLayout(ApiClient client) {
        menuPanel.setLayout(new javax.swing.BoxLayout(menuPanel, javax.swing.BoxLayout.Y_AXIS));
        menuPanel.setOpaque(false);
        for (Map.Entry<String, String> entry : titles.entrySet()) {
            String key = entry.getKey();
            JButton button = new JButton(entry.getValue());
            button.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
            button.setMaximumSize(new Dimension(220, 34));
            button.setAlignmentX(0);
            button.setFocusPainted(false);
            button.setBorder(null);
            button.setForeground(Color.WHITE);
            button.setBackground(SIDEBAR);
            button.setOpaque(true);
            button.putClientProperty("key", key);
            button.addActionListener(e -> show(key));
            menuPanel.add(button);
            menuPanel.add(javax.swing.Box.createVerticalStrut(4));
        }
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(SIDEBAR);
        sidebar.setPreferredSize(new Dimension(220, 0));
        sidebar.setBorder(BorderFactory.createEmptyBorder(20, 16, 20, 16));
        sidebar.add(menuPanel, BorderLayout.NORTH);

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(Color.WHITE);
        top.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Ui.BORDER),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)));
        JLabel user = new JLabel(client.fullName() + "  •  " + client.role());
        user.setFont(user.getFont().deriveFont(java.awt.Font.BOLD, 12f));
        JButton change = Ui.button("Ganti Password", () -> Dialogs.changePassword(this, client));
        JButton logout = Ui.button("Keluar", () -> {
            if (Ui.confirm(this, "Tutup sesi dan keluar?")) {
                try {
                    client.logout();
                } catch (Exception ignored) {
                    // Sesi akan kedaluwarsa di server.
                }
                DesktopApp.showLogin();
                dispose();
            }
        });
        JPanel right = new JPanel();
        right.setOpaque(false);
        right.add(change);
        right.add(logout);

        JPanel center = new JPanel();
        center.setOpaque(false);
        branchCombo.setName("branch.selector");
        branchCombo.setToolTipText("Cabang aktif");
        branchCombo.addActionListener(e -> {
            if (loadingBranches || branchRows.isEmpty()) {
                return;
            }
            int index = branchCombo.getSelectedIndex();
            Long branchId = null;
            int offset = client.owner() ? 1 : 0;
            if (index >= offset) {
                int row = index - offset;
                if (row >= 0 && row < branchRows.size()) {
                    branchId = ApiClient.lng(branchRows.get(row).get("id"));
                }
            }
            client.activeBranchId(branchId);
            if (active != null) {
                show(active);
            }
        });
        center.add(new JLabel("Cabang:"));
        center.add(branchCombo);

        top.add(user, BorderLayout.WEST);
        top.add(center, BorderLayout.CENTER);
        top.add(right, BorderLayout.EAST);

        content.setOpaque(false);
        add(sidebar, BorderLayout.WEST);
        add(top, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
        if (!cards.isEmpty()) {
            show(cards.keySet().iterator().next());
        }
    }

    /** Tampilkan panel kunci tertentu dan muat ulang bila mendukung Refreshable. */
    public void show(String key) {
        if (!cards.containsKey(key) || !titles.containsKey(key)) {
            Ui.error(this, "Menu tidak tersedia untuk peran Anda.");
            return;
        }
        active = key;
        JPanel panel = cards.get(key);
        if (panel instanceof Refreshable refreshable) {
            refreshable.refresh();
        }
        cardLayout.show(content, key);
        // highlight sidebar
        for (int i = 0; i < menuPanel.getComponentCount(); i++) {
            if (menuPanel.getComponent(i) instanceof JButton button && button.getClientProperty("key") != null) {
                String target = String.valueOf(button.getClientProperty("key"));
                button.setBackground(target.equals(key) ? SIDEBAR_ACTIVE : SIDEBAR);
            }
        }
    }
}

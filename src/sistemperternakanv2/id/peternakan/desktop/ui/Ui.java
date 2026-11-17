package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;
import id.peternakan.desktop.api.ApiClientException;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Komponen dan gaya UI bersama: palet hijau/putih, tabel, format, dan eksekusi
 * latar belakang agar EDT tidak diblokir.
 */
public final class Ui {
    public static final Color GREEN = new Color(0x16, 0x65, 0x34);
    public static final Color GREEN_DARK = new Color(0x14, 0x53, 0x2D);
    public static final Color GREEN_LIGHT = new Color(0xDC, 0xFC, 0xE7);
    public static final Color BG = new Color(0xF8, 0xFA, 0xFC);
    public static final Color MUTED = new Color(0x47, 0x55, 0x69);
    public static final Color DANGER = new Color(0xB9, 0x1C, 0x1C);
    public static final Color BORDER = new Color(0xE2, 0xE8, 0xF0);

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(new Locale("id", "ID"));

    private Ui() {
    }

    public static JLabel title(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 20f));
        label.setForeground(new Color(0x0F, 0x17, 0x2A));
        return label;
    }

    public static JLabel subtitle(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        return label;
    }

    public static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(MUTED);
        return label;
    }

    public static JLabel value(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 18f));
        return label;
    }

    public static JPanel card(JComponent content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    public static JPanel header(String title, String subtitle) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new javax.swing.BoxLayout(text, javax.swing.BoxLayout.Y_AXIS));
        text.add(title(title));
        text.add(subtitle(subtitle));
        panel.add(text, BorderLayout.WEST);
        return panel;
    }

    public static JTextField field(String value) {
        JTextField field = new JTextField(value == null ? "" : value);
        field.setPreferredSize(new Dimension(220, 30));
        return field;
    }

    public static JTextArea area(String value) {
        JTextArea area = new JTextArea(value == null ? "" : value, 3, 24);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    public static JComboBox<String> combo(String... values) {
        return new JComboBox<>(values);
    }

    public static JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> action.run());
        return button;
    }

    public static JButton primary(String text, Runnable action) {
        JButton button = button(text, action);
        button.setBackground(GREEN);
        button.setForeground(Color.WHITE);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createEmptyBorder(7, 16, 7, 16));
        return button;
    }

    public static JButton danger(String text, Runnable action) {
        JButton button = button(text, action);
        button.setForeground(DANGER);
        return button;
    }

    public static JPanel toolbar(Component... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        panel.setOpaque(false);
        for (Component component : components) {
            panel.add(component);
        }
        return panel;
    }

    public static JPanel form() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        return panel;
    }

    public static void addRow(JPanel form, int row, String label, JComponent input) {
        GridBagConstraints left = new GridBagConstraints();
        left.gridx = 0;
        left.gridy = row;
        left.anchor = GridBagConstraints.WEST;
        left.insets = new Insets(4, 0, 4, 12);
        form.add(label(label), left);
        GridBagConstraints right = new GridBagConstraints();
        right.gridx = 1;
        right.gridy = row;
        right.weightx = 1;
        right.fill = GridBagConstraints.HORIZONTAL;
        right.insets = new Insets(4, 0, 4, 0);
        form.add(input, right);
    }

    public static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    public static JTable table(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        table.setRowHeight(26);
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.getTableHeader().setReorderingAllowed(false);
        return table;
    }

    public static JTable table(DefaultTableModel model, int... moneyColumns) {
        JTable table = table(model);
        money(table, moneyColumns);
        return table;
    }

    /** Pastikan nilai sel yang sedang diedit masuk ke model sebelum dibaca. */
    public static void commitEdit(JTable table) {
        if (table != null && table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
    }

    public static JScrollPane scroll(JTable table) {
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        return scroll;
    }

    public static void setRows(DefaultTableModel model, List<Map<String, Object>> rows, String... keys) {
        model.setRowCount(0);
        for (Map<String, Object> row : rows) {
            Object[] values = new Object[keys.length];
            for (int i = 0; i < keys.length; i++) {
                values[i] = row.get(keys[i]);
            }
            model.addRow(values);
        }
    }

    public static Long selectedId(JTable table, int idColumn) {
        int row = table.getSelectedRow();
        if (row < 0) {
            return null;
        }
        Object value = table.getValueAt(row, idColumn);
        if (value == null) {
            return null;
        }
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }

    public static void money(JTable table, int... columns) {
        for (int column : columns) {
            table.getColumnModel().getColumn(column).setCellRenderer((t, value, selected, focus, row, col) -> {
                JLabel label = new JLabel(formatMoney(value));
                label.setOpaque(true);
                label.setHorizontalAlignment(JLabel.RIGHT);
                label.setBackground(selected ? t.getSelectionBackground() : Color.WHITE);
                label.setForeground(selected ? t.getSelectionForeground() : new Color(0x0F, 0x17, 0x2A));
                return label;
            });
        }
    }

    public static String formatMoney(Object value) {
        if (value == null) {
            return "";
        }
        BigDecimal amount;
        try {
            amount = value instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            return String.valueOf(value);
        }
        return "Rp " + String.format(Locale.GERMANY, "%,.0f", amount.doubleValue());
    }

    public static String formatDate(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        try {
            if (text.length() >= 10) {
                return LocalDate.parse(text.substring(0, 10)).format(DISPLAY_DATE);
            }
        } catch (Exception ignored) {
            // Tampilkan apa adanya.
        }
        return text;
    }

    public static String today() {
        return LocalDate.now().toString();
    }

    public static String monthStart() {
        return LocalDate.now().withDayOfMonth(1).toString();
    }

    public static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Informasi", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void error(Component parent, Throwable error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        JOptionPane.showMessageDialog(parent, message, "Kesalahan", JOptionPane.ERROR_MESSAGE);
    }

    public static void error(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Kesalahan", JOptionPane.ERROR_MESSAGE);
    }

    public static boolean confirm(Component parent, String message) {
        return JOptionPane.showConfirmDialog(parent, message, "Konfirmasi", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    public static String input(Component parent, String message, String initial) {
        return JOptionPane.showInputDialog(parent, message, initial);
    }

    public static <T> void async(Component parent, String loading, Callable<T> work, Consumer<T> done) {
        async(parent, loading, work, done, () -> {
        });
    }

    /**
     * Jalankan pekerjaan di background dan selalu panggil finished di EDT,
     * termasuk ketika pekerjaan atau callback hasil gagal.
     */
    public static <T> void async(Component parent, String loading, Callable<T> work, Consumer<T> done,
                                 Runnable finished) {
        async(parent, loading, work, done, cause -> {
            if (cause instanceof ApiClientException api) {
                error(parent, api);
            } else {
                error(parent, cause);
            }
        }, finished);
    }

    static <T> void async(Component parent, String loading, Callable<T> work, Consumer<T> done,
                          Consumer<Throwable> failed, Runnable finished) {
        JComponent component = parent instanceof JComponent c ? c : null;
        if (component != null) {
            component.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                if (component != null) {
                    component.setCursor(Cursor.getDefaultCursor());
                }
                completeAsync(this::get, done, failed, finished);
            }
        }.execute();
    }

    static <T> void completeAsync(Callable<T> result, Consumer<T> done,
                                  Consumer<Throwable> failed, Runnable finished) {
        try {
            done.accept(result.call());
        } catch (Exception e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            failed.accept(cause);
        } finally {
            finished.run();
        }
    }

    public static void later(Runnable runnable) {
        SwingUtilities.invokeLater(runnable);
    }

    public static String str(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    public static long lng(Map<String, Object> row, String key) {
        return ApiClient.lng(row.get(key));
    }

    public static JPanel statCard(String label, String value) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        panel.add(label(label), BorderLayout.NORTH);
        panel.add(value(value), BorderLayout.CENTER);
        return panel;
    }
}

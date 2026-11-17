package id.peternakan.desktop.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiTableEditTest {
    @Test
    void commitEditMenyimpanNilaiSelYangSedangDiedit() {
        DefaultTableModel model = new DefaultTableModel(new Object[]{"Keterangan", "Qty", "Harga"}, 0);
        model.addRow(new Object[]{"Kambing", 1, 2500000});
        JTable table = Ui.table(model);

        assertTrue(table.editCellAt(0, 1));
        JTextField editor = (JTextField) table.getEditorComponent();
        editor.setText("5");
        Ui.commitEdit(table);

        assertEquals("5", String.valueOf(model.getValueAt(0, 1)));
    }
}

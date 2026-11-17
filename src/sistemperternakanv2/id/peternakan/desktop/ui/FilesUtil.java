package id.peternakan.desktop.ui;

import id.peternakan.desktop.api.ApiClient;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Util simpan file laporan ke folder pilihan pengguna. */
final class FilesUtil {
    private FilesUtil() {
    }

    static void save(JPanel parent, String filename, byte[] bytes) {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setSelectedFile(new java.io.File(filename));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.io.File file = chooser.getSelectedFile();
        try {
            Files.write(file.toPath(), bytes);
            JOptionPane.showMessageDialog(parent, "Berhasil disimpan ke " + file, "Simpan",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            Ui.error(parent, e);
        }
    }

    static void preview(JPanel parent, String title, byte[] pdf) {
        new PdfPreviewDialog(parent, title, pdf);
    }
}

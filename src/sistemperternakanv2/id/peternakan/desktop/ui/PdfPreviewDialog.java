package id.peternakan.desktop.ui;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;

/** Dialog pratinjau PDF menggunakan PDFBox untuk merender halaman. */
final class PdfPreviewDialog extends JDialog {
    private static final float RENDER_SCALE = 1.4f;

    PdfPreviewDialog(JPanel parent, String title, byte[] pdf) {
        setTitle(title);
        setSize(900, 700);
        setLocationRelativeTo(parent);
        JPanel pages = new JPanel();
        pages.setLayout(new BoxLayout(pages, BoxLayout.Y_AXIS));
        pages.setBackground(new Color(0x52, 0x57, 0x64));
        try (PDDocument document = PDDocument.load(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = renderer.renderImage(page, RENDER_SCALE);
                pages.add(imagePanel(image));
                pages.add(Box.createVerticalStrut(10));
            }
        } catch (IOException e) {
            Ui.error(parent, e);
            return;
        }
        setContentPane(new JScrollPane(pages));
        setVisible(true);
    }

    private static javax.swing.JPanel imagePanel(java.awt.image.BufferedImage image) {
        javax.swing.JLabel label = new javax.swing.JLabel();
        label.setIcon(new javax.swing.ImageIcon(image));
        javax.swing.JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.add(label, java.awt.BorderLayout.CENTER);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        return panel;
    }
}

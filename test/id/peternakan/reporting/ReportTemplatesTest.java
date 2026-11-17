package id.peternakan.reporting;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Memastikan semua template dapat dikompilasi dan menghasilkan PDF valid.
 * Parameter dibaca dari deklarasi template agar tipe data selalu cocok.
 */
class ReportTemplatesTest {
    private static final List<String> TEMPLATES = List.of(
            "invoice_penjualan", "kuitansi_pembayaran", "laba_rugi_per_kambing",
            "laba_rugi_periode", "laporan_persediaan", "piutang_pelanggan",
            "rekap_keuntungan_kerugian", "rekap_operasional", "utang_supplier");

    @Test
    void semuaTemplateMenghasilkanPdf() throws Exception {
        ReportEngine engine = new ReportEngine();
        for (String template : TEMPLATES) {
            Map<String, Object> params = parameters(template);
            byte[] pdf = engine.pdf(template, params, List.of());
            assertTrue(pdf.length > 1000, "PDF terlalu kecil untuk " + template);
            String header = new String(pdf, 0, 4, StandardCharsets.ISO_8859_1);
            assertTrue(header.startsWith("%PDF"), "Header PDF tidak valid untuk " + template);
        }
    }

    @Test
    void semuaTemplateDapatDikompilasi() throws Exception {
        for (String template : TEMPLATES) {
            try (InputStream in = getClass().getResourceAsStream("/reports/" + template + ".jrxml")) {
                assertTrue(in != null, "Template hilang: " + template);
                net.sf.jasperreports.engine.JasperCompileManager.compileReport(in);
            }
        }
    }

    private Map<String, Object> parameters(String template) throws Exception {
        String xml;
        try (InputStream in = getClass().getResourceAsStream("/reports/" + template + ".jrxml")) {
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Map<String, Object> params = new LinkedHashMap<>();
        Matcher matcher = Pattern.compile("<parameter name=\"([^\"]+)\" class=\"([^\"]+)\"/>").matcher(xml);
        while (matcher.find()) {
            params.put(matcher.group(1), value(matcher.group(2)));
        }
        return params;
    }

    private Object value(String type) {
        return switch (type) {
            case "java.math.BigDecimal" -> new BigDecimal("1000.00");
            case "java.lang.Integer" -> 1;
            case "java.lang.Long" -> 1L;
            case "java.lang.Boolean" -> Boolean.TRUE;
            case "java.util.Date" -> new java.util.Date();
            default -> "Contoh";
        };
    }
}

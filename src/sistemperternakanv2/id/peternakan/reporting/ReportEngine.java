package id.peternakan.reporting;

import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimplePdfExporterConfiguration;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Semua PDF dibuat dari template .jrxml di classpath /reports.
 * Template adalah bagian source code; pengguna tidak dapat mengunggah template/SQL.
 */
public final class ReportEngine {
    private static final Map<String, JasperReport> CACHE = new ConcurrentHashMap<>();

    public byte[] pdf(String templateName, Map<String, Object> params, List<Map<String, Object>> rows) {
        try {
            JasperReport report = compiled(templateName);
            List<Map<String, ?>> safeRows = rows == null ? List.of() : List.copyOf(rows);
            JRMapCollectionDataSource data = new JRMapCollectionDataSource(safeRows);
            JasperPrint print = JasperFillManager.fillReport(report, params, data);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            JRPdfExporter exporter = new JRPdfExporter();
            exporter.setExporterInput(new SimpleExporterInput(print));
            exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));
            SimplePdfExporterConfiguration config = new SimplePdfExporterConfiguration();
            config.setMetadataTitle(String.valueOf(params.getOrDefault("REPORT_TITLE", templateName)));
            exporter.setConfiguration(config);
            exporter.exportReport();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Gagal membuat PDF: " + e.getMessage(), e);
        }
    }

    public String csv(List<String> headers, List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append(String.join(";", headers.stream().map(ReportEngine::csvCell).toList())).append("\r\n");
        for (Map<String, Object> row : rows) {
            sb.append(String.join(";", headers.stream()
                    .map(h -> csvCell(row.get(h) == null ? "" : String.valueOf(row.get(h)))).toList())).append("\r\n");
        }
        return sb.toString();
    }

    public byte[] templateBytes(String templateName) {
        try (InputStream in = resource(templateName)) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("Template tidak ditemukan: " + templateName, e);
        }
    }

    private JasperReport compiled(String templateName) throws Exception {
        JasperReport cached = CACHE.get(templateName);
        if (cached != null) {
            return cached;
        }
        try (InputStream in = resource(templateName)) {
            JasperReport report = JasperCompileManager.compileReport(in);
            CACHE.put(templateName, report);
            return report;
        }
    }

    private InputStream resource(String templateName) {
        String path = "/reports/" + templateName + ".jrxml";
        InputStream in = ReportEngine.class.getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("Template laporan tidak ditemukan: " + path);
        }
        return in;
    }

    private static String csvCell(String value) {
        String v = value == null ? "" : value;
        if (v.contains(";") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    public static String utf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

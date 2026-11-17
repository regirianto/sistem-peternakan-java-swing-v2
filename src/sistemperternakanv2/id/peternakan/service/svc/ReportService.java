package id.peternakan.service.svc;

import id.peternakan.domain.Money;
import id.peternakan.domain.Permissions;
import id.peternakan.reporting.ReportEngine;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.api.ApiResponse;
import id.peternakan.service.db.Db;
import id.peternakan.service.repo.ProfitRepository;
import id.peternakan.service.repo.ReportRepository;
import id.peternakan.service.repo.SaleRepository;
import id.peternakan.service.repo.PaymentRepository;
import id.peternakan.service.security.Audit;
import id.peternakan.service.security.SessionInfo;
import id.peternakan.service.support.BranchAccess;
import id.peternakan.service.support.Settings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ReportService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    private final Db db;
    private final ReportRepository reports;
    private final ProfitRepository profits;
    private final SaleRepository sales;
    private final PaymentRepository payments;
    private final ReportEngine engine = new ReportEngine();

    public ReportService(Db db, ReportRepository reports, ProfitRepository profits, SaleRepository sales,
                         PaymentRepository payments) {
        this.db = db;
        this.reports = reports;
        this.profits = profits;
        this.sales = sales;
        this.payments = payments;
    }

    public Map<String, Object> dashboard(SessionInfo session, String from, String to, Long branchId) {
        session.require(Permissions.DASHBOARD_VIEW);
        LocalDate start = parseDate(from, LocalDate.now().withDayOfMonth(1));
        LocalDate end = parseDate(to, LocalDate.now());
        Long filter = BranchAccess.filter(db, session, branchId);
        Map<String, Object> data = reports.dashboard(start, end, filter);
        if (!session.canViewFinance()) {
            List<String> financial = List.of("revenue", "hpp", "expenses", "feedUsage", "losses",
                    "periodResult", "receivables", "payables");
            financial.forEach(data::remove);
        }
        return data;
    }

    public Map<String, Object> inventory(SessionInfo session, String asOf, Long branchId) {
        session.requireFinance();
        Long filter = BranchAccess.filter(db, session, branchId);
        return reports.inventory(parseDate(asOf, LocalDate.now()), filter);
    }

    public Map<String, Object> analytics(SessionInfo session, Long branchId) {
        session.requireFinance();
        Long filter = BranchAccess.filter(db, session, branchId);
        return reports.analytics(filter);
    }

    public Map<String, Object> periodProfitLoss(SessionInfo session, String from, String to, Long branchId) {
        session.requireFinance();
        Long filter = BranchAccess.filter(db, session, branchId);
        return reports.periodProfitLoss(parseDate(from, LocalDate.now().withDayOfMonth(1)),
                parseDate(to, LocalDate.now()), filter);
    }

    public Map<String, Object> profitList(SessionInfo session, String from, String to, String q, int page,
                                          int size, Long branchId) {
        session.requireFinance();
        LocalDate start = parseDate(from, LocalDate.now().withDayOfMonth(1));
        LocalDate end = parseDate(to, LocalDate.now());
        Long filter = BranchAccess.filter(db, session, branchId);
        return Map.of("items", profits.list(start.toString(), end.toString(), q, page, size, filter),
                "total", profits.count(start.toString(), end.toString(), q, filter));
    }

    public Map<String, Object> profitRecap(SessionInfo session, String from, String to, Long branchId) {
        session.requireFinance();
        LocalDate start = parseDate(from, LocalDate.now().withDayOfMonth(1));
        LocalDate end = parseDate(to, LocalDate.now());
        Long filter = BranchAccess.filter(db, session, branchId);
        return profits.recap(start.toString(), end.toString(), filter);
    }

    /**
     * Membuat PDF/CSV laporan. Dataset diambil dari layanan (bukan dari klien),
     * permission dan filter diperiksa sebelum template diisi.
     */
    public ApiResponse generate(SessionInfo session, String report, Map<String, String> query) {
        String format = query.getOrDefault("format", "pdf").toLowerCase(Locale.ROOT);
        if (!"pdf".equals(format) && !"csv".equals(format)) {
            throw ApiException.badRequest("Format harus pdf atau csv.");
        }
        String from = query.get("from");
        String to = query.get("to");
        String q = query.get("q");
        LocalDate start = parseDate(from, LocalDate.now().withDayOfMonth(1));
        LocalDate end = parseDate(to, LocalDate.now());
        String periodLabel = "Periode " + start.format(DATE) + " s.d. " + end.format(DATE);
        Long branchId = BranchAccess.filter(db, session, branchQuery(query));
        String branchLabel = branchName(branchId);

        Map<String, Object> params = baseParams(session, periodLabel, filterLabel(query, branchLabel));
        List<Map<String, Object>> rows;
        List<String> headers;

        switch (report) {
            case "invoice_penjualan" -> {
                session.require(Permissions.SALE_MANAGE);
                long id = requireId(query, "id");
                Map<String, Object> sale = sales.find(id);
                BranchAccess.requireBranch(db, session, Db.lng(sale, "branch_id"));
                rows = new ArrayList<>();
                for (Map<String, Object> item : cast(sale.get("items"))) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("goat_code", item.get("goat_code") != null
                            ? item.get("goat_code") : item.get("batch_no"));
                    row.put("method", "PER_KG".equals(item.get("pricing_method")) ? "Per kg" : "Per ekor");
                    row.put("weight", item.get("weight_kg"));
                    row.put("rate", item.get("rate"));
                    row.put("net", item.get("net"));
                    rows.add(row);
                }
                params.put("REPORT_TITLE", "Invoice Penjualan");
                params.put("DOC_NO", Db.str(sale, "doc_no"));
                params.put("PARTY_NAME", Db.str(sale, "customer_name"));
                params.put("DOC_DATE", dateText(sale.get("sale_date")));
                params.put("TOTAL_NET", Db.dec(sale, "total_net"));
                params.put("NOTE", "Barang yang sudah dibeli tidak dapat dikembalikan tanpa persetujuan owner.");
                params.put("PERIOD_LABEL", "Tanggal " + dateText(sale.get("sale_date")));
                headers = List.of("goat_code", "method", "weight", "rate", "net");
                return "csv".equals(format) ? ApiResponse.csv(engine.csv(headers, rows))
                        : ApiResponse.pdf(engine.pdf(report, params, rows));
            }
            case "kuitansi_pembayaran" -> {
                session.require(Permissions.PAYMENT_RECORD);
                long id = requireId(query, "id");
                Map<String, Object> payment = payments.find(id);
                BranchAccess.requireBranch(db, session, Db.lng(payment, "branch_id"));
                rows = List.of();
                params.put("REPORT_TITLE", "Kuitansi Pembayaran");
                params.put("DOC_NO", Db.str(payment, "doc_no"));
                params.put("PARTY_NAME", Db.str(payment, "party_name"));
                params.put("DOC_DATE", dateText(payment.get("payment_date")));
                params.put("AMOUNT", Db.dec(payment, "amount"));
                params.put("METHOD", Db.str(payment, "method"));
                params.put("ACCOUNT_NAME", Db.str(payment, "branch_name"));
                params.put("SOURCE_DOC", firstNonNull(Db.str(payment, "sale_doc_no"),
                        Db.str(payment, "purchase_doc_no"), Db.str(payment, "source_type")));
                params.put("NOTE", Db.str(payment, "notes") == null ? "" : Db.str(payment, "notes"));
                params.put("PERIOD_LABEL", "Tanggal " + dateText(payment.get("payment_date")));
                headers = List.of("label", "value");
                return ApiResponse.pdf(engine.pdf(report, params, rows));
            }
            case "laporan_persediaan" -> {
                session.requireFinance();
                Map<String, Object> inv = reports.inventory(end, branchId);
                rows = normalize(cast(inv.get("goats")));
                params.put("REPORT_TITLE", "Laporan Persediaan Kambing");
                params.put("GOAT_COUNT", String.valueOf(inv.get("goatCount")));
                params.put("GOAT_VALUE", Db.dec(inv, "goatValue"));
                params.put("FEED_VALUE", Db.dec(inv, "feedValue"));
                params.put("TOTAL_VALUE", Db.dec(inv, "goatValue").add(Db.dec(inv, "feedValue")));
                params.put("NOTE", "Nilai persediaan berdasarkan harga perolehan dan rata-rata tertimbang pakan.");
                headers = List.of("code", "supplier_name", "pen_name", "latest_weight", "health_status",
                        "sale_readiness", "acquisition_cost");
            }
            case "laba_rugi_per_kambing" -> {
                session.requireFinance();
                rows = normalize(profits.list(start.toString(), end.toString(), q, 1, 100000, branchId));
                BigDecimal revenue = sum(rows, "revenue");
                BigDecimal hpp = sum(rows, "hpp");
                BigDecimal cost = sum(rows, "direct_cost").add(sum(rows, "allocated_cost"));
                params.put("REPORT_TITLE", "Laba Rugi per Kambing");
                params.put("TOTAL_REVENUE", revenue);
                params.put("TOTAL_HPP", hpp);
                params.put("TOTAL_COST", cost);
                params.put("TOTAL_PROFIT", revenue.subtract(hpp).subtract(cost));
                params.put("NOTE", "Laba manajerial = penjualan bersih - HPP - beban langsung - beban bersama teralokasi.");
                headers = List.of("code", "supplier_name", "entry_date", "sale_date", "holding_days", "revenue",
                        "hpp", "direct_cost", "allocated_cost", "profit", "margin_percent", "snapshot_status");
            }
            case "rekap_keuntungan_kerugian" -> {
                session.requireFinance();
                Map<String, Object> recap = profits.recap(start.toString(), end.toString(), branchId);
                rows = normalize(profits.list(start.toString(), end.toString(), q, 1, 100000, branchId));
                params.put("REPORT_TITLE", "Rekap Keuntungan dan Kerugian");
                params.put("SOLD_COUNT", String.valueOf(recap.getOrDefault("sold_count", 0)));
                params.put("PROFIT_COUNT", String.valueOf(recap.getOrDefault("profit_count", 0)));
                params.put("LOSS_COUNT", String.valueOf(recap.getOrDefault("loss_count", 0)));
                params.put("TOTAL_PROFIT", recap.get("total_profit") == null ? Money.ZERO : recap.get("total_profit"));
                params.put("AVG_PROFIT", recap.get("avg_profit") == null ? Money.ZERO : recap.get("avg_profit"));
                params.put("NOTE", "Rekap laba manajerial per kambing yang terjual pada periode.");
                headers = List.of("code", "sale_date", "revenue", "hpp", "direct_cost", "allocated_cost",
                        "profit", "margin_percent", "snapshot_status");
            }
            case "laba_rugi_periode" -> {
                session.requireFinance();
                Map<String, Object> pl = reports.periodProfitLoss(start, end, branchId);
                rows = normalize(cast(pl.get("reconciliation")));
                params.put("REPORT_TITLE", "Laba Rugi Periode");
                params.put("REVENUE", pl.get("revenue"));
                params.put("HPP", pl.get("hpp"));
                params.put("GROSS_MARGIN", pl.get("grossMargin"));
                params.put("EXPENSES", pl.get("expenses"));
                params.put("FEED_USAGE", pl.get("feedUsage"));
                params.put("LOSSES", pl.get("losses"));
                params.put("PERIOD_RESULT", pl.get("periodResult"));
                params.put("MARGIN_PERCENT", pl.get("marginPercent"));
                params.put("NOTE", String.valueOf(pl.get("note")));
                headers = List.of("label", "amount");
            }
            case "utang_supplier" -> {
                session.requireFinance();
                rows = normalizeWithAgeText(payments.payables(branchId));
                params.put("REPORT_TITLE", "Laporan Utang Supplier");
                params.put("TOTAL", sum(rows, "remaining"));
                params.put("NOTE", "Utang dihitung dari nilai kambing diterima dikurangi pembayaran efektif.");
                headers = List.of("doc_no", "party_name", "purchase_date", "due_date", "received_value",
                        "paid", "remaining", "age_days");
            }
            case "piutang_pelanggan" -> {
                session.requireFinance();
                rows = normalizeWithAgeText(payments.receivables(branchId));
                params.put("REPORT_TITLE", "Laporan Piutang Pelanggan");
                params.put("TOTAL", sum(rows, "remaining"));
                params.put("NOTE", "Piutang dihitung dari nilai penjualan bersih dikurangi pembayaran efektif.");
                headers = List.of("doc_no", "party_name", "sale_date", "payment_status", "total_net",
                        "paid", "remaining", "age_days");
            }
            case "rekap_operasional" -> {
                session.require(Permissions.REPORT_OPERATIONAL);
                rows = normalize(reports.operationalRecap(start, end, branchId));
                params.put("REPORT_TITLE", "Rekap Operasional");
                params.put("NOTE", "Rekap kegiatan operasional pada periode terpilih.");
                headers = List.of("entry_date", "module", "doc_no", "party_name", "description", "amount");
            }
            default -> throw ApiException.notFound("Laporan tidak dikenal: " + report);
        }

        auditExport(session, report, format);
        return "csv".equals(format) ? ApiResponse.csv(engine.csv(headers, rows))
                : ApiResponse.pdf(engine.pdf(report, params, rows));
    }

    private Map<String, Object> baseParams(SessionInfo session, String periodLabel, String filterLabel) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("REPORT_TITLE", "");
        params.put("COMPANY_NAME", Settings.get(db, "company.name", "Peternakan Kambing"));
        params.put("COMPANY_ADDRESS", Settings.get(db, "company.address", ""));
        params.put("COMPANY_PHONE", Settings.get(db, "company.phone", ""));
        params.put("PERIOD_LABEL", periodLabel);
        params.put("FILTER_LABEL", filterLabel);
        params.put("PRINTED_AT", LocalDateTime.now().format(STAMP));
        params.put("PRINTED_BY", session.fullName() + " (" + session.roleCode() + ")");
        params.put("NOTE", Settings.get(db, "report.footer", ""));
        return params;
    }

    private void auditExport(SessionInfo session, String report, String format) {
        db.withTx(tx -> {
            Audit.log(tx, session, "REPORTS", "EXPORT", "reports", report, null,
                    Map.of("format", format), "Ekspor laporan");
            return null;
        });
    }

    private String filterLabel(Map<String, String> query, String branchLabel) {
        StringBuilder sb = new StringBuilder("Cabang: ").append(branchLabel);
        if (query.get("q") != null && !query.get("q").isBlank()) {
            sb.append(" · Pencarian: ").append(query.get("q"));
        }
        return sb.toString();
    }

    private Long branchQuery(Map<String, String> query) {
        String value = query.get("branchId");
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Parameter branchId tidak valid.");
        }
    }

    private String branchName(Long branchId) {
        if (branchId == null) {
            return "Semua Cabang";
        }
        Map<String, Object> branch = db.queryOne("SELECT name FROM branches WHERE id = ?", branchId);
        return branch == null ? "-" : Db.str(branch, "name");
    }

    private long requireId(Map<String, String> query, String key) {
        String value = query.get(key);
        if (value == null || value.isBlank()) {
            throw ApiException.badRequest("Parameter " + key + " wajib diisi.");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Parameter " + key + " tidak valid.");
        }
    }

    private static LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            throw ApiException.badRequest("Tanggal tidak valid: " + value);
        }
    }

    private static String dateText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDate d) {
            return d.format(DATE);
        }
        return String.valueOf(value);
    }

    private static String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> cast(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private static List<Map<String, Object>> normalize(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> copy = new LinkedHashMap<>();
            row.forEach((key, value) -> {
                if (value instanceof LocalDate d) {
                    copy.put(key, d.format(DATE));
                } else if (value instanceof LocalDateTime dt) {
                    copy.put(key, dt.format(STAMP));
                } else {
                    copy.put(key, value);
                }
            });
            out.add(copy);
        }
        return out;
    }

    /** Normalisasi + teks untuk kolom umur agar tumpang-tindih tipe JDBC tidak error. */
    private static List<Map<String, Object>> normalizeWithAgeText(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = normalize(rows);
        for (Map<String, Object> row : out) {
            if (row.get("age_days") instanceof Number n) {
                row.put("age_days", n.intValue());
            }
        }
        return out;
    }

    private static BigDecimal sum(List<Map<String, Object>> rows, String key) {
        BigDecimal total = Money.ZERO;
        for (Map<String, Object> row : rows) {
            Object value = row.get(key);
            if (value instanceof BigDecimal b) {
                total = total.add(b);
            } else if (value instanceof Number n) {
                total = total.add(BigDecimal.valueOf(n.doubleValue()));
            }
        }
        return total;
    }
}

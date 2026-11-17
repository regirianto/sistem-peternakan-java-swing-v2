package id.peternakan.service.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import id.peternakan.domain.Permissions;
import id.peternakan.service.config.AppConfig;
import id.peternakan.service.db.Db;
import id.peternakan.service.repo.AllocationRepository;
import id.peternakan.service.repo.AttachmentRepository;
import id.peternakan.service.repo.BranchRepository;
import id.peternakan.service.repo.ExpenseRepository;
import id.peternakan.service.repo.FeedRepository;
import id.peternakan.service.repo.GoatRepository;
import id.peternakan.service.repo.JournalRepository;
import id.peternakan.service.repo.MeatRepository;
import id.peternakan.service.repo.PartyRepository;
import id.peternakan.service.repo.PaymentRepository;
import id.peternakan.service.repo.PenRepository;
import id.peternakan.service.repo.PeriodRepository;
import id.peternakan.service.repo.ProfitRepository;
import id.peternakan.service.repo.PurchaseRepository;
import id.peternakan.service.repo.ReceiptRepository;
import id.peternakan.service.repo.ReportRepository;
import id.peternakan.service.repo.SaleRepository;
import id.peternakan.service.security.AuthService;
import id.peternakan.service.security.SessionInfo;
import id.peternakan.service.svc.AllocationService;
import id.peternakan.service.svc.AuditService;
import id.peternakan.service.svc.BackupService;
import id.peternakan.service.svc.BranchService;
import id.peternakan.service.svc.ExpenseService;
import id.peternakan.service.svc.FeedService;
import id.peternakan.service.svc.GoatService;
import id.peternakan.service.svc.JournalService;
import id.peternakan.service.svc.MeatService;
import id.peternakan.service.svc.PartyService;
import id.peternakan.service.svc.PaymentService;
import id.peternakan.service.svc.PenService;
import id.peternakan.service.svc.PeriodService;
import id.peternakan.service.svc.ProfitService;
import id.peternakan.service.svc.PurchaseService;
import id.peternakan.service.svc.ReceiptService;
import id.peternakan.service.svc.ReportService;
import id.peternakan.service.svc.SaleService;
import id.peternakan.service.svc.SettingsService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Layanan aplikasi internal (HTTP JSON). Klien Swing tidak pernah memegang
 * kredensial MySQL; semua pemeriksaan izin dan perhitungan dilakukan di sini.
 */
public final class ApiServer {
    @FunctionalInterface
    private interface Handler {
        Object handle(Ctx ctx) throws Exception;
    }

    private record Route(String method, String pattern, boolean auth, Handler handler) {
    }

    private record Ctx(SessionInfo session, Map<String, String> path, Map<String, String> query,
                       Map<String, Object> body, String requestId) {
        long id(String key) {
            String value = path.get(key);
            try {
                return Long.parseLong(value);
            } catch (Exception e) {
                throw ApiException.badRequest("Parameter " + key + " tidak valid.");
            }
        }

        String str(String key) {
            return body.get(key) == null ? null : String.valueOf(body.get(key));
        }

        String query(String key) {
            return query.get(key);
        }

        int page() {
            return intQuery("page", 1);
        }

        int size() {
            return Math.min(intQuery("size", 25), 500);
        }

        int intQuery(String key, int fallback) {
            String value = query.get(key);
            if (value == null || value.isBlank()) {
                return fallback;
            }
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw ApiException.badRequest("Parameter " + key + " tidak valid.");
            }
        }

        Long longQuery(String key) {
            String value = query.get(key);
            return value == null || value.isBlank() ? null : Long.parseLong(value);
        }
    }

    private final Db db;
    private final AuthService auth;
    private final GoatService goats;
    private final PenService pens;
    private final PartyService parties;
    private final PurchaseService purchases;
    private final ReceiptService receipts;
    private final SaleService sales;
    private final MeatService meat;
    private final PaymentService payments;
    private final FeedService feed;
    private final ExpenseService expenses;
    private final AllocationService allocations;
    private final ProfitService profits;
    private final ReportService reports;
    private final JournalService journals;
    private final PeriodService periods;
    private final SettingsService settings;
    private final BranchService branches;
    private final BackupService backups;
    private final AuditService audits;
    private final List<Route> routes = new ArrayList<>();
    private HttpServer server;

    public ApiServer(Db db, AppConfig config) {
        this.db = db;
        this.auth = new AuthService(db);
        GoatRepository goatRepo = new GoatRepository(db);
        PenRepository penRepo = new PenRepository(db);
        PartyRepository partyRepo = new PartyRepository(db);
        PurchaseRepository purchaseRepo = new PurchaseRepository(db);
        ReceiptRepository receiptRepo = new ReceiptRepository(db);
        SaleRepository saleRepo = new SaleRepository(db);
        PaymentRepository paymentRepo = new PaymentRepository(db);
        FeedRepository feedRepo = new FeedRepository(db);
        MeatRepository meatRepo = new MeatRepository(db);
        ExpenseRepository expenseRepo = new ExpenseRepository(db);
        ProfitRepository profitRepo = new ProfitRepository(db);
        ReportRepository reportRepo = new ReportRepository(db);
        BranchRepository branchRepo = new BranchRepository(db);
        AttachmentRepository attachmentRepo = new AttachmentRepository(db);
        AllocationRepository allocationRepo = new AllocationRepository(db, goatRepo, profitRepo);

        this.goats = new GoatService(db, goatRepo, penRepo, profitRepo, attachmentRepo, config);
        this.pens = new PenService(db, penRepo);
        this.parties = new PartyService(db, partyRepo);
        this.purchases = new PurchaseService(db, purchaseRepo);
        this.receipts = new ReceiptService(db, receiptRepo, purchaseRepo, goatRepo, penRepo);
        this.sales = new SaleService(db, saleRepo, profitRepo);
        this.meat = new MeatService(db, meatRepo);
        this.payments = new PaymentService(db, paymentRepo, saleRepo, purchaseRepo);
        this.feed = new FeedService(db, feedRepo, allocationRepo);
        this.expenses = new ExpenseService(db, expenseRepo, allocationRepo);
        this.allocations = new AllocationService(db, allocationRepo);
        this.profits = new ProfitService(db, profitRepo);
        this.reports = new ReportService(db, reportRepo, profitRepo, saleRepo, paymentRepo);
        this.journals = new JournalService(db, new JournalRepository(db));
        this.periods = new PeriodService(db, new PeriodRepository(db));
        this.settings = new SettingsService(db);
        this.backups = new BackupService(db, config);
        this.audits = new AuditService(db);
        this.branches = new BranchService(db, branchRepo);

        register();
    }

    private void register() {
        // Publik
        post("/api/login", false, ctx -> auth.login(ctx.str("username"), ctx.str("password"), ctx.requestId()));
        get("/api/health", false, ctx -> Map.of("status", "ok", "time", LocalDateTime.now().toString()));
        get("/api/bootstrap/status", false, ctx -> auth.bootstrapStatus());
        post("/api/bootstrap/owner", false, ctx -> auth.bootstrapOwner(ctx.str("username"), ctx.str("password"),
                ctx.str("fullName")));
        get("/api/public/info", false, ctx -> settings.publicInfo());

        // Sesi
        get("/api/me", true, ctx -> sessionMap(ctx.session()));
        post("/api/logout", true, ctx -> {
            auth.logout(ctx.session(), ctx.requestId());
            return Map.of("loggedOut", true);
        });
        post("/api/change-password", true, ctx -> {
            auth.changePassword(ctx.session(), ctx.str("currentPassword"), ctx.str("newPassword"));
            return Map.of("changed", true);
        });

        // Dashboard
        get("/api/dashboard", true, ctx -> reports.dashboard(ctx.session(), ctx.query("from"), ctx.query("to"),
                ctx.longQuery("branchId")));

        // Kandang
        get("/api/pens", true, ctx -> pens.list(ctx.session(), ctx.longQuery("branchId")));
        get("/api/pens/{id}", true, ctx -> pens.find(ctx.session(), ctx.id("id")));
        post("/api/pens", true, ctx -> pens.create(ctx.session(), ctx.body()));
        put("/api/pens/{id}", true, ctx -> pens.update(ctx.session(), ctx.id("id"), ctx.body()));

        // Cabang
        get("/api/branches", true, ctx -> branches.accessible(ctx.session()));
        get("/api/branches/all", true, ctx -> branches.all(ctx.session()));
        post("/api/branches", true, ctx -> branches.create(ctx.session(), ctx.body()));
        put("/api/branches/{id}", true, ctx -> branches.update(ctx.session(), ctx.id("id"), ctx.body()));
        put("/api/users/{id}/branches", true, ctx -> branches.assignUserBranches(ctx.session(),
                ctx.id("id"), ctx.body()));

        // Kambing
        get("/api/goats", true, ctx -> goats.list(ctx.session(), ctx.query("q"), ctx.query("status"),
                ctx.query("health"), ctx.query("readiness"), ctx.longQuery("supplierId"), ctx.longQuery("penId"),
                ctx.query("sort"), ctx.page(), ctx.size(), ctx.longQuery("branchId")));
        get("/api/goats/sellable", true, ctx -> goats.sellable(ctx.session(), ctx.longQuery("branchId")));
        get("/api/goats/{id}", true, ctx -> goats.find(ctx.session(), ctx.id("id")));
        get("/api/goats/{id}/photo", true, ctx -> {
            Map<String, Object> photo = goats.photo(ctx.session(), ctx.id("id"));
            return ApiResponse.download(String.valueOf(photo.get("mimeType")),
                    (byte[]) photo.get("bytes"), String.valueOf(photo.get("filename")));
        });
        post("/api/goats/{id}/photo", true, ctx -> goats.setPhoto(ctx.session(), ctx.id("id"), ctx.body()));
        post("/api/goats/{id}/weights", true, ctx -> goats.addWeight(ctx.session(), ctx.id("id"), ctx.body()));
        post("/api/goats/{id}/health", true, ctx -> goats.addHealth(ctx.session(), ctx.id("id"), ctx.body()));
        post("/api/goats/{id}/move", true, ctx -> goats.move(ctx.session(), ctx.id("id"), ctx.body()));
        post("/api/goats/{id}/readiness", true, ctx -> goats.readiness(ctx.session(), ctx.id("id"), ctx.body()));
        post("/api/goats/{id}/death", true, ctx -> goats.death(ctx.session(), ctx.id("id"), ctx.body(),
                ctx.requestId()));
        post("/api/goats/{id}/profit/finalize", true, ctx -> profits.finalize(ctx.session(), ctx.id("id")));
        get("/api/goats/{id}/profit", true, ctx -> profits.snapshots(ctx.session(), ctx.id("id")));
        get("/api/goats/{id}/profit/live", true, ctx -> profits.live(ctx.session(), ctx.id("id")));

        // Pihak
        get("/api/parties", true, ctx -> parties.list(ctx.session(), ctx.query("role"), ctx.query("q"),
                ctx.page(), ctx.size()));
        get("/api/parties/{id}", true, ctx -> parties.find(ctx.id("id")));
        post("/api/parties", true, ctx -> parties.create(ctx.session(), ctx.body()));
        put("/api/parties/{id}", true, ctx -> parties.update(ctx.session(), ctx.id("id"), ctx.body()));

        // Pembelian
        get("/api/purchases", true, ctx -> purchases.list(ctx.session(), ctx.query("q"), ctx.query("status"),
                ctx.page(), ctx.size(), ctx.longQuery("branchId")));
        get("/api/purchases/open", true, ctx -> purchases.open(ctx.session(), ctx.longQuery("branchId")));
        get("/api/purchases/{id}", true, ctx -> purchases.find(ctx.session(), ctx.id("id")));
        post("/api/purchases", true, ctx -> purchases.create(ctx.session(), ctx.body()));
        post("/api/purchases/{id}/confirm", true, ctx -> purchases.confirm(ctx.session(), ctx.id("id")));
        post("/api/purchases/{id}/cancel", true, ctx -> purchases.cancel(ctx.session(), ctx.id("id"),
                ctx.str("reason")));
        post("/api/purchases/{id}/close-remaining", true, ctx -> purchases.closeRemaining(ctx.session(),
                ctx.id("id"), ctx.str("reason")));

        // Penerimaan
        get("/api/receipts", true, ctx -> receipts.list(ctx.session(), ctx.query("q"), ctx.page(), ctx.size(),
                ctx.longQuery("branchId")));
        get("/api/receipts/{id}", true, ctx -> receipts.find(ctx.session(), ctx.id("id")));
        post("/api/receipts", true, ctx -> receipts.create(ctx.session(), ctx.body(), ctx.requestId()));

        // Penjualan
        get("/api/sales", true, ctx -> sales.list(ctx.session(), ctx.query("q"), ctx.query("status"),
                ctx.page(), ctx.size(), ctx.longQuery("branchId")));
        get("/api/sales/pending-approvals", true, ctx -> sales.pendingApprovals(ctx.session(),
                ctx.longQuery("branchId")));
        get("/api/sales/{id}", true, ctx -> sales.find(ctx.session(), ctx.id("id")));
        post("/api/sales", true, ctx -> sales.saveDraft(ctx.session(), null, ctx.body()));
        put("/api/sales/{id}", true, ctx -> sales.saveDraft(ctx.session(), ctx.id("id"), ctx.body()));
        post("/api/sales/{id}/confirm", true, ctx -> sales.confirm(ctx.session(), ctx.id("id"), ctx.requestId()));
        post("/api/sales/{id}/request-approval", true, ctx -> sales.requestApproval(ctx.session(), ctx.id("id"),
                ctx.str("reason")));
        post("/api/sales/{id}/approve", true, ctx -> sales.approve(ctx.session(), ctx.id("id"), ctx.str("reason")));
        post("/api/sales/{id}/cancel", true, ctx -> sales.cancel(ctx.session(), ctx.id("id"), ctx.str("reason"),
                ctx.requestId()));

        // Pemotongan dan daging
        get("/api/slaughter", true, ctx -> meat.orders(ctx.session(), ctx.query("q"), ctx.query("status"),
                ctx.page(), ctx.size(), ctx.longQuery("branchId")));
        get("/api/slaughter/{id}", true, ctx -> meat.find(ctx.session(), ctx.id("id")));
        post("/api/slaughter", true, ctx -> meat.saveDraft(ctx.session(), null, ctx.body()));
        put("/api/slaughter/{id}", true, ctx -> meat.saveDraft(ctx.session(), ctx.id("id"), ctx.body()));
        post("/api/slaughter/{id}/post", true, ctx -> meat.post(ctx.session(), ctx.id("id"), ctx.requestId()));
        post("/api/slaughter/{id}/cancel", true, ctx -> meat.cancel(ctx.session(), ctx.id("id"),
                ctx.str("reason"), ctx.requestId()));
        get("/api/meat/batches", true, ctx -> meat.batches(ctx.session(), ctx.query("q"),
                !"all".equalsIgnoreCase(ctx.query("open")), ctx.longQuery("branchId")));
        get("/api/meat/movements", true, ctx -> meat.movements(ctx.session(), ctx.longQuery("batchId"),
                ctx.longQuery("branchId")));
        post("/api/meat/waste", true, ctx -> meat.waste(ctx.session(), ctx.body(), ctx.requestId()));

        // Pembayaran
        get("/api/payments", true, ctx -> payments.list(ctx.session(), ctx.query("q"), ctx.query("direction"),
                ctx.query("from"), ctx.query("to"), ctx.page(), ctx.size(), ctx.longQuery("branchId")));
        get("/api/payments/receivables", true, ctx -> payments.receivables(ctx.session(), ctx.longQuery("branchId")));
        get("/api/payments/payables", true, ctx -> payments.payables(ctx.session(), ctx.longQuery("branchId")));
        get("/api/payments/{id}", true, ctx -> payments.find(ctx.session(), ctx.id("id")));
        post("/api/payments", true, ctx -> payments.create(ctx.session(), ctx.body(), ctx.requestId()));
        post("/api/payments/{id}/reverse", true, ctx -> payments.reverse(ctx.session(), ctx.id("id"),
                ctx.str("reason"), ctx.requestId()));

        // Pakan
        get("/api/feed/items", true, ctx -> feed.items(ctx.session(), ctx.longQuery("branchId")));
        post("/api/feed/items", true, ctx -> feed.createItem(ctx.session(), ctx.body()));
        post("/api/feed/receive", true, ctx -> feed.receive(ctx.session(), ctx.body(), ctx.requestId()));
        post("/api/feed/usage", true, ctx -> feed.usage(ctx.session(), ctx.body(), ctx.requestId()));
        get("/api/feed/movements", true, ctx -> feed.movements(ctx.session(), ctx.longQuery("feedItemId"),
                ctx.longQuery("branchId")));
        get("/api/feed/stock-value", true, ctx -> feed.stockValue(ctx.session(), ctx.longQuery("branchId")));

        // Pengeluaran
        get("/api/expenses", true, ctx -> expenses.list(ctx.session(), ctx.query("q"), ctx.query("status"),
                ctx.query("from"), ctx.query("to"), ctx.page(), ctx.size(), ctx.longQuery("branchId")));
        get("/api/expenses/categories", true, ctx -> expenses.categories(ctx.session()));
        post("/api/expenses/categories", true, ctx -> expenses.createCategory(ctx.session(), ctx.body()));
        get("/api/expenses/postable", true, ctx -> expenses.postable(ctx.session(), ctx.longQuery("branchId")));
        get("/api/expenses/{id}", true, ctx -> expenses.find(ctx.session(), ctx.id("id")));
        post("/api/expenses", true, ctx -> expenses.create(ctx.session(), ctx.body()));
        post("/api/expenses/{id}/post", true, ctx -> expenses.post(ctx.session(), ctx.id("id"), ctx.requestId()));
        post("/api/expenses/{id}/reverse", true, ctx -> expenses.reverse(ctx.session(), ctx.id("id"),
                ctx.str("reason"), ctx.requestId()));

        // Alokasi
        get("/api/allocations/pending", true, ctx -> allocations.pending(ctx.session(),
                ctx.longQuery("branchId")));
        get("/api/allocations/cost-sources/{id}", true, ctx -> allocations.batches(ctx.session(), ctx.id("id")));
        get("/api/allocations/batches/{id}", true, ctx -> allocations.allocations(ctx.session(), ctx.id("id")));
        post("/api/allocations/cost-sources/{id}/preview", true, ctx -> allocations.preview(ctx.session(),
                ctx.id("id")));
        post("/api/allocations/cost-sources/{id}/finalize", true, ctx -> allocations.finalize(ctx.session(),
                ctx.id("id")));
        get("/api/allocations/by-document", true, ctx -> allocations.costSourceByDocument(ctx.session(),
                ctx.query("type"), ctx.longQuery("id") == null ? 0 : ctx.longQuery("id")));

        // Laporan JSON
        get("/api/reports/inventory", true, ctx -> reports.inventory(ctx.session(), ctx.query("asOf"),
                ctx.longQuery("branchId")));
        get("/api/reports/analytics", true, ctx -> reports.analytics(ctx.session(), ctx.longQuery("branchId")));
        get("/api/reports/profit-loss", true, ctx -> reports.periodProfitLoss(ctx.session(), ctx.query("from"),
                ctx.query("to"), ctx.longQuery("branchId")));
        get("/api/reports/profit-per-goat", true, ctx -> reports.profitList(ctx.session(), ctx.query("from"),
                ctx.query("to"), ctx.query("q"), ctx.page(), ctx.size(), ctx.longQuery("branchId")));
        get("/api/reports/profit-recap", true, ctx -> reports.profitRecap(ctx.session(), ctx.query("from"),
                ctx.query("to"), ctx.longQuery("branchId")));
        get("/api/reports/{key}/file", true, ctx -> {
            Map<String, String> q = new LinkedHashMap<>(ctx.query());
            if (ctx.requestId() != null) {
                q.put("requestId", ctx.requestId());
            }
            ApiResponse response = reports.generate(ctx.session(), ctx.path().get("key"), q);
            String format = q.getOrDefault("format", "pdf");
            String ext = "csv".equalsIgnoreCase(format) ? "csv" : "pdf";
            String name = "laporan-" + ctx.path().get("key") + "-"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "." + ext;
            return ApiResponse.download(response.contentType(), response.body(), name);
        });

        // Jurnal
        get("/api/journals", true, ctx -> journals.list(ctx.session(), ctx.query("from"), ctx.query("to"),
                ctx.query("sourceType"), ctx.query("q"), ctx.page(), ctx.size(), ctx.longQuery("branchId")));
        get("/api/journals/reconciliation", true, ctx -> journals.reconciliation(ctx.session(),
                ctx.query("from"), ctx.query("to"), ctx.longQuery("branchId")));
        get("/api/journals/by-source", true, ctx -> journals.bySource(ctx.session(), ctx.query("type"),
                ctx.longQuery("id") == null ? 0 : ctx.longQuery("id")));
        get("/api/journals/{id}", true, ctx -> journals.find(ctx.session(), ctx.id("id")));

        // Periode
        get("/api/periods", true, ctx -> periods.list(ctx.session()));
        post("/api/periods/close", true, ctx -> periods.close(ctx.session(), ctx.body()));
        post("/api/periods/reopen", true, ctx -> periods.reopen(ctx.session(), ctx.body()));

        // Pengguna
        get("/api/users", true, ctx -> {
            ctx.session().require(Permissions.USER_MANAGE);
            return Map.of("items", auth.listUsers(), "roles", auth.listRoles());
        });
        get("/api/users/permissions", true, ctx -> auth.permissionCatalog(ctx.session()));
        put("/api/users/roles/{role}/permissions", true, ctx -> auth.updateRolePermissions(ctx.session(),
                ctx.path().get("role"), stringList(ctx.body().get("permissions"))));
        post("/api/users", true, ctx -> {
            ctx.session().require(Permissions.USER_MANAGE);
            return auth.createUser(ctx.session(), ctx.str("username"), ctx.str("password"), ctx.str("fullName"),
                    ctx.str("roleCode"));
        });
        put("/api/users/{id}", true, ctx -> {
            ctx.session().require(Permissions.USER_MANAGE);
            return auth.updateUser(ctx.session(), ctx.id("id"), ctx.str("fullName"), ctx.str("roleCode"),
                    ctx.body().get("active") == null ? null : Boolean.parseBoolean(ctx.str("active")),
                    ctx.str("newPassword"));
        });

        // Audit
        get("/api/audit", true, ctx -> audits.list(ctx.session(), ctx.query("q"), ctx.query("module"),
                ctx.query("from"), ctx.query("to"), ctx.page(), ctx.size()));
        get("/api/audit/modules", true, ctx -> audits.modules(ctx.session()));

        // Pengaturan
        get("/api/settings", true, ctx -> settings.all(ctx.session()));
        put("/api/settings", true, ctx -> settings.update(ctx.session(), ctx.body()));

        // Backup
        get("/api/backups", true, ctx -> backups.list(ctx.session()));
        post("/api/backups", true, ctx -> backups.backup(ctx.session()));
        post("/api/backups/restore", true, ctx -> backups.restore(ctx.session(), ctx.body()));
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8765), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(12));
        server.start();
    }

    public void start(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(12));
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            Map<String, Object> body = readBody(exchange);
            String requestId = exchange.getRequestHeaders().getFirst("X-Request-Id");
            if (requestId == null || requestId.isBlank()) {
                Object fromBody = body.get("requestId");
                requestId = fromBody == null ? null : String.valueOf(fromBody);
            }

            Route match = null;
            Map<String, String> pathParams = Map.of();
            for (Route route : routes) {
                if (!route.method().equalsIgnoreCase(method)) {
                    continue;
                }
                Map<String, String> params = match(route.pattern(), path);
                if (params != null) {
                    match = route;
                    pathParams = params;
                    break;
                }
            }
            if (match == null) {
                write(exchange, 404, "application/json",
                        Json.MAPPER.writeValueAsBytes(Map.of("error", "Endpoint tidak ditemukan.")), null);
                return;
            }

            SessionInfo session = null;
            if (match.auth()) {
                String header = exchange.getRequestHeaders().getFirst("Authorization");
                String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
                session = auth.authenticate(token);
                if (session.mustChangePassword() && !List.of("/api/change-password", "/api/logout", "/api/me")
                        .contains(path)) {
                    throw ApiException.forbidden("Anda harus mengganti password terlebih dahulu.");
                }
            }

            Object result = match.handler().handle(new Ctx(session, pathParams, query, body, requestId));
            if (result instanceof ApiResponse response) {
                write(exchange, response.status(), response.contentType(), response.body(), response.filename());
            } else {
                write(exchange, 200, "application/json", Json.MAPPER.writeValueAsBytes(result), null);
            }
        } catch (ApiException e) {
            write(exchange, e.status(), "application/json",
                    Json.MAPPER.writeValueAsBytes(Map.of("error", e.getMessage())), null);
        } catch (java.time.format.DateTimeParseException e) {
            write(exchange, 400, "application/json",
                    Json.MAPPER.writeValueAsBytes(Map.of("error", "Format tanggal tidak valid (yyyy-MM-dd).")),
                    null);
        } catch (NumberFormatException e) {
            write(exchange, 400, "application/json",
                    Json.MAPPER.writeValueAsBytes(Map.of("error", "Format angka tidak valid.")), null);
        } catch (Exception e) {
            e.printStackTrace();
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            write(exchange, 500, "application/json",
                    Json.MAPPER.writeValueAsBytes(Map.of("error", "Kesalahan layanan: " + message)), null);
        } finally {
            exchange.close();
        }
    }

    private static void write(HttpExchange exchange, int status, String contentType, byte[] body, String filename)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if (filename != null) {
            exchange.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename=\"" + filename.replace("\"", "") + "\"");
        }
        exchange.sendResponseHeaders(status, body == null ? -1 : body.length);
        if (body != null) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        }
    }

    private static Map<String, Object> readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) {
                return new LinkedHashMap<>();
            }
            Object parsed = Json.MAPPER.readValue(bytes, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                map.forEach((k, v) -> result.put(String.valueOf(k), v));
                return result;
            }
            throw ApiException.badRequest("Body harus berupa objek JSON.");
        }
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String pair : raw.split("&")) {
            int idx = pair.indexOf('=');
            String key = idx < 0 ? pair : pair.substring(0, idx);
            String value = idx < 0 ? "" : pair.substring(idx + 1);
            result.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return result;
    }

    private static Map<String, String> match(String pattern, String path) {
        String[] p = pattern.split("/");
        String[] a = path.split("/");
        if (p.length != a.length) {
            return null;
        }
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < p.length; i++) {
            if (p[i].startsWith("{") && p[i].endsWith("}")) {
                params.put(p[i].substring(1, p[i].length() - 1), a[i]);
            } else if (!p[i].equals(a[i])) {
                return null;
            }
        }
        return params;
    }

    private Map<String, Object> sessionMap(SessionInfo session) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", session.userId());
        out.put("username", session.username());
        out.put("fullName", session.fullName());
        out.put("role", session.roleCode());
        out.put("mustChangePassword", session.mustChangePassword());
        out.put("permissions", session.permissions().stream().sorted().toList());
        out.put("ownerOnly", Permissions.OWNER_ONLY);
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return null;
    }

    private void get(String pattern, boolean auth, Handler handler) {
        routes.add(new Route("GET", pattern, auth, handler));
    }

    private void post(String pattern, boolean auth, Handler handler) {
        routes.add(new Route("POST", pattern, auth, handler));
    }

    private void put(String pattern, boolean auth, Handler handler) {
        routes.add(new Route("PUT", pattern, auth, handler));
    }

    public static String newRequestId() {
        return UUID.randomUUID().toString();
    }
}

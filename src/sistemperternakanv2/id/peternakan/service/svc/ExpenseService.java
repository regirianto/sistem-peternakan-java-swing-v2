package id.peternakan.service.svc;

import id.peternakan.domain.Money;
import id.peternakan.domain.Permissions;
import id.peternakan.service.api.ApiException;
import id.peternakan.service.db.Db;
import id.peternakan.service.repo.AllocationRepository;
import id.peternakan.service.repo.ExpenseRepository;
import id.peternakan.service.security.Audit;
import id.peternakan.service.security.SessionInfo;
import id.peternakan.service.support.BranchAccess;
import id.peternakan.service.support.DocNo;
import id.peternakan.service.support.Idempotency;
import id.peternakan.service.support.Journal;
import id.peternakan.service.support.PeriodGuard;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class ExpenseService {
    private final Db db;
    private final ExpenseRepository repo;
    private final AllocationRepository allocations;

    public ExpenseService(Db db, ExpenseRepository repo, AllocationRepository allocations) {
        this.db = db;
        this.repo = repo;
        this.allocations = allocations;
    }

    public Map<String, Object> list(SessionInfo session, String q, String status, String from, String to,
                                    int page, int size, Long branchId) {
        session.require(Permissions.EXPENSE_MANAGE);
        Long filter = BranchAccess.filter(db, session, branchId);
        return Map.of("items", repo.list(q, status, from, to, page, size, filter),
                "total", repo.count(q, status, from, to, filter));
    }

    public Map<String, Object> find(SessionInfo session, long id) {
        session.require(Permissions.EXPENSE_MANAGE);
        Map<String, Object> expense = repo.find(id);
        BranchAccess.requireBranch(db, session, Db.lng(expense, "branch_id"));
        return expense;
    }

    public Map<String, Object> categories(SessionInfo session) {
        session.require(Permissions.EXPENSE_MANAGE);
        return Map.of("items", repo.categories());
    }

    public Map<String, Object> createCategory(SessionInfo session, Map<String, Object> body) {
        session.require(Permissions.SETTINGS_MANAGE);
        long id = db.withTx(tx -> {
            long newId = repo.createCategory(tx, PartyService.str(body, "code"), PartyService.str(body, "name"),
                    PartyService.str(body, "treatment"), PartyService.bool(body, "allocatable", false));
            Audit.log(tx, session, "EXPENSES", "CREATE_CATEGORY", "expense_categories",
                    String.valueOf(newId), null, body, null);
            return newId;
        });
        return Map.of("id", id);
    }

    public Map<String, Object> create(SessionInfo session, Map<String, Object> body) {
        session.require(Permissions.EXPENSE_MANAGE);
        long categoryId = Long.parseLong(String.valueOf(body.get("categoryId")));
        LocalDate date = LocalDate.parse(String.valueOf(body.get("expenseDate")));
        BigDecimal amount = Money.of(String.valueOf(body.get("amount")));
        Long partyId = body.get("partyId") == null ? null : Long.parseLong(String.valueOf(body.get("partyId")));
        Long penId = body.get("scopePenId") == null ? null : Long.parseLong(String.valueOf(body.get("scopePenId")));
        Long goatId = body.get("directGoatId") == null ? null : Long.parseLong(String.valueOf(body.get("directGoatId")));
        LocalDate serviceStart = body.get("serviceStart") == null ? null
                : LocalDate.parse(String.valueOf(body.get("serviceStart")));
        LocalDate serviceEnd = body.get("serviceEnd") == null ? null
                : LocalDate.parse(String.valueOf(body.get("serviceEnd")));
        boolean allocatable = PartyService.bool(body, "allocatable", false);
        if (goatId != null) {
            allocatable = false;
        }
        if (allocatable && (serviceStart == null || serviceEnd == null)) {
            throw ApiException.badRequest("Biaya bersama yang dialokasikan wajib memiliki rentang layanan.");
        }
        final boolean allocatableFinal = allocatable;
        long branchId = BranchAccess.requireWrite(db, session, PartyService.longVal(body, "branchId"));
        Map<String, Object> created = db.withTx(tx -> {
            if (penId != null) {
                Map<String, Object> pen = tx.queryOne("SELECT branch_id FROM pens WHERE id = ?", penId);
                if (pen == null) {
                    throw ApiException.notFound("Kandang tidak ditemukan.");
                }
                if (Db.lng(pen, "branch_id") != branchId) {
                    throw ApiException.badRequest("Kandang berada di cabang lain.");
                }
            }
            if (goatId != null) {
                Map<String, Object> goat = tx.queryOne("SELECT branch_id FROM goats WHERE id = ?", goatId);
                if (goat == null) {
                    throw ApiException.notFound("Kambing tidak ditemukan.");
                }
                if (Db.lng(goat, "branch_id") != branchId) {
                    throw ApiException.badRequest("Kambing berada di cabang lain.");
                }
            }
            Map<String, Object> category = tx.queryOne("SELECT * FROM expense_categories WHERE id=? AND active=1",
                    categoryId);
            if (category == null) {
                throw ApiException.badRequest("Kategori pengeluaran tidak ditemukan atau tidak aktif.");
            }
            String docNo = DocNo.next(tx, "EX", date);
            long newId = repo.create(tx, docNo, branchId, categoryId, date, amount, partyId,
                    PartyService.str(body, "purpose"), PartyService.str(body, "notes"), allocatableFinal,
                    serviceStart, serviceEnd, penId, goatId, session.userId());
            Audit.log(tx, session, "EXPENSES", "CREATE", "expenses", String.valueOf(newId), null,
                    Map.of("docNo", docNo, "amount", amount, "categoryId", categoryId), null);
            return Map.<String, Object>of("id", newId, "docNo", docNo);
        });
        return created;
    }

    public Map<String, Object> post(SessionInfo session, long id, String requestId) {
        session.require(Permissions.EXPENSE_POST);
        return db.withTx(tx -> Idempotency.run(tx, requestId, session.userId(), "EXPENSE_POST", () -> {
            Map<String, Object> expense = repo.lock(tx, id);
            BranchAccess.requireBranch(db, session, Db.lng(expense, "branch_id"));
            if (!"DRAFT".equals(Db.str(expense, "status"))) {
                throw ApiException.conflict("Hanya pengeluaran DRAFT yang dapat diposting.");
            }
            LocalDate date = Db.date(expense, "expense_date");
            PeriodGuard.requireOpen(tx, date);
            Map<String, Object> category = tx.queryOne("SELECT * FROM expense_categories WHERE id=?",
                    Db.lng(expense, "category_id"));
            String treatment = Db.str(category, "treatment");
            String debitCode = repo.accountCodeForTreatment(treatment);
            String cashCode = "1101";
            BigDecimal amount = Db.dec(expense, "amount");
            Journal.post(tx, date, Db.lng(expense, "branch_id"), "EXPENSE", id, "EXPENSE:" + id,
                    "Pengeluaran " + Db.str(expense, "doc_no"), session.userId(), List.of(
                            Journal.Line.debit(debitCode, amount, Db.str(category, "name")),
                            Journal.Line.credit(cashCode, amount, "Kas/bank keluar")));
            repo.setStatus(tx, id, "POSTED");

            Long directGoatId = expense.get("direct_goat_id") == null ? null
                    : ((Number) expense.get("direct_goat_id")).longValue();
            Long scopePenId = expense.get("scope_pen_id") == null ? null
                    : ((Number) expense.get("scope_pen_id")).longValue();
            boolean allocatable = Db.lng(expense, "allocatable") == 1
                    && !"ASET".equals(treatment) && !"PEROLEHAN".equals(treatment);
            Map<String, Object> allocation = Map.of("allocatable", false);
            if (allocatable) {
                allocations.createCostSource(tx, "EXPENSE", id, amount,
                        Db.date(expense, "service_start"), Db.date(expense, "service_end"),
                        scopePenId, directGoatId, true);
                allocation = Map.of("allocatable", true, "pendingFinalization", true);
            }
            Audit.log(tx, session, "EXPENSES", "POST", "expenses", String.valueOf(id),
                    Map.of("status", "DRAFT"), Map.of("status", "POSTED", "journalDebit", debitCode), null);
            return Map.<String, Object>of("id", id, "status", "POSTED", "allocation", allocation);
        }));
    }

    public Map<String, Object> reverse(SessionInfo session, long id, String reason, String requestId) {
        if (!session.isOwner()) {
            throw ApiException.forbidden("Pembatalan pengeluaran hanya oleh owner.");
        }
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("Alasan pembatalan wajib diisi.");
        }
        return db.withTx(tx -> Idempotency.run(tx, requestId, session.userId(), "EXPENSE_REVERSE", () -> {
            Map<String, Object> expense = repo.lock(tx, id);
            BranchAccess.requireBranch(db, session, Db.lng(expense, "branch_id"));
            if (!"POSTED".equals(Db.str(expense, "status"))) {
                throw ApiException.conflict("Hanya pengeluaran POSTED yang dapat dibatalkan.");
            }
            LocalDate date = LocalDate.now();
            PeriodGuard.requireOpen(tx, date);
            List<Map<String, Object>> entries = tx.query("""
                    SELECT id FROM journal_entries WHERE source_type='EXPENSE' AND source_id=? AND status='POSTED'
                    """, id);
            for (Map<String, Object> entry : entries) {
                Journal.reverse(tx, Db.lng(entry, "id"), date, session.userId(), reason);
            }
            tx.update("UPDATE expenses SET status='REVERSED', reversed_by_id=? WHERE id=?",
                    session.userId(), id);
            tx.update("""
                    UPDATE allocation_batches SET status='REVERSED'
                    WHERE cost_source_id IN (SELECT id FROM cost_sources WHERE source_type='EXPENSE' AND source_id=?)
                      AND status='POSTED'
                    """, id);
            Audit.log(tx, session, "EXPENSES", "REVERSE", "expenses", String.valueOf(id),
                    Map.of("status", "POSTED"), Map.of("status", "REVERSED"), reason);
            return Map.<String, Object>of("id", id, "status", "REVERSED");
        }));
    }

    public Map<String, Object> postable(SessionInfo session, Long branchId) {
        session.require(Permissions.EXPENSE_POST);
        Long filter = BranchAccess.filter(db, session, branchId);
        return Map.of("items", repo.postable(filter));
    }
}

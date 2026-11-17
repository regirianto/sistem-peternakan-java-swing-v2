package id.peternakan.domain;

import java.util.List;

/**
 * Kode permission per aksi. Permission keuangan hanya dimiliki role OWNER.
 */
public final class Permissions {
    public static final String DASHBOARD_VIEW = "dashboard.view";
    public static final String GOAT_READ = "goat.read";
    public static final String GOAT_CREATE = "goat.create";
    public static final String GOAT_UPDATE = "goat.update";
    public static final String GOAT_OPERATIONAL = "goat.operational";
    public static final String PEN_MANAGE = "pen.manage";
    public static final String WEIGHT_RECORD = "weight.record";
    public static final String HEALTH_RECORD = "health.record";
    public static final String HEALTH_MANAGE = "health.manage";
    public static final String FEED_MANAGE = "feed.manage";
    public static final String FEED_RECORD = "feed.record";
    public static final String SUPPLIER_MANAGE = "supplier.manage";
    public static final String CUSTOMER_MANAGE = "customer.manage";
    public static final String PURCHASE_MANAGE = "purchase.manage";
    public static final String PURCHASE_CONFIRM = "purchase.confirm";
    public static final String RECEIPT_MANAGE = "receipt.manage";
    public static final String SALE_MANAGE = "sale.manage";
    public static final String SALE_CONFIRM = "sale.confirm";
    public static final String SALE_APPROVE = "sale.approve";
    public static final String PAYMENT_RECORD = "payment.record";
    public static final String PAYMENT_REVERSE = "payment.reverse";
    public static final String EXPENSE_MANAGE = "expense.manage";
    public static final String EXPENSE_POST = "expense.post";
    public static final String ALLOCATION_FINALIZE = "allocation.finalize";
    public static final String FINANCE_VIEW = "finance.view";
    public static final String FINANCE_EXPORT = "finance.export";
    public static final String REPORT_OPERATIONAL = "report.operational";
    public static final String PERIOD_CLOSE = "period.close";
    public static final String USER_MANAGE = "user.manage";
    public static final String AUDIT_READ = "audit.read";
    public static final String BACKUP_MANAGE = "backup.manage";
    public static final String SETTINGS_MANAGE = "settings.manage";
    public static final String PRICE_MANAGE = "price.manage";
    public static final String SLAUGHTER_MANAGE = "slaughter.manage";
    public static final String MEAT_VIEW = "meat.view";
    public static final String BRANCH_MANAGE = "branch.manage";

    /**
     * Permission yang tidak boleh diberikan ke role selain OWNER.
     */
    public static final List<String> OWNER_ONLY = List.of(
            FINANCE_VIEW, FINANCE_EXPORT, PERIOD_CLOSE, USER_MANAGE, AUDIT_READ, BACKUP_MANAGE,
            SETTINGS_MANAGE, SALE_APPROVE, PAYMENT_REVERSE, ALLOCATION_FINALIZE, BRANCH_MANAGE);

    private Permissions() {
    }
}

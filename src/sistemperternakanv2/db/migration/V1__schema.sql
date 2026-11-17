-- V1: skema awal Sistem Manajemen Peternakan Kambing (MySQL 8.4 / InnoDB)
-- Semua nilai uang DECIMAL(18,2); berat/jumlah DECIMAL(12,3).

CREATE TABLE roles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(60) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE permissions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(60) NOT NULL UNIQUE,
    description VARCHAR(160) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES roles(id),
    CONSTRAINT fk_rp_perm FOREIGN KEY (permission_id) REFERENCES permissions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(60) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    role_id BIGINT NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    must_change_password TINYINT(1) NOT NULL DEFAULT 0,
    session_version INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sessions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NOT NULL,
    revoked_at DATETIME NULL,
    session_version INT NOT NULL,
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_sessions_user (user_id, revoked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE activity_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    occurred_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id BIGINT NULL,
    username VARCHAR(60) NULL,
    module VARCHAR(40) NOT NULL,
    action VARCHAR(60) NOT NULL,
    entity VARCHAR(60) NULL,
    entity_id VARCHAR(80) NULL,
    before_json JSON NULL,
    after_json JSON NULL,
    reason VARCHAR(500) NULL,
    correlation_id VARCHAR(80) NULL,
    INDEX idx_log_time (occurred_at),
    INDEX idx_log_user (user_id),
    INDEX idx_log_module (module),
    INDEX idx_log_entity (entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE parties (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    phone VARCHAR(40) NULL,
    address VARCHAR(300) NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    notes VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_party_name (name),
    INDEX idx_party_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE party_roles (
    party_id BIGINT NOT NULL,
    role_code VARCHAR(20) NOT NULL,
    PRIMARY KEY (party_id, role_code),
    CONSTRAINT fk_pr_party FOREIGN KEY (party_id) REFERENCES parties(id),
    CONSTRAINT ck_pr_role CHECK (role_code IN ('SUPPLIER','CUSTOMER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    capacity INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AKTIF',
    notes VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 1,
    CONSTRAINT ck_pen_capacity CHECK (capacity > 0),
    CONSTRAINT ck_pen_status CHECK (status IN ('AKTIF','NONAKTIF'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE purchases (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    doc_no VARCHAR(40) NOT NULL UNIQUE,
    supplier_id BIGINT NOT NULL,
    purchase_date DATE NOT NULL,
    due_date DATE NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    notes VARCHAR(500) NULL,
    total_amount DECIMAL(18,2) NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at DATETIME NULL,
    version INT NOT NULL DEFAULT 1,
    CONSTRAINT fk_purchase_supplier FOREIGN KEY (supplier_id) REFERENCES parties(id),
    INDEX idx_purchase_status (status, purchase_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE purchase_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    purchase_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    description VARCHAR(200) NOT NULL,
    qty INT NOT NULL,
    unit_price DECIMAL(18,2) NOT NULL,
    discount DECIMAL(18,2) NOT NULL DEFAULT 0,
    received_qty INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pi_purchase FOREIGN KEY (purchase_id) REFERENCES purchases(id),
    UNIQUE KEY uk_pi_line (purchase_id, line_no),
    CONSTRAINT ck_pi_qty CHECK (qty > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE receipts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    doc_no VARCHAR(40) NOT NULL UNIQUE,
    purchase_id BIGINT NOT NULL,
    receipt_date DATE NOT NULL,
    shipping_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    notes VARCHAR(500) NULL,
    total_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_receipt_purchase FOREIGN KEY (purchase_id) REFERENCES purchases(id),
    INDEX idx_receipt_date (receipt_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE goats (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(40) NOT NULL UNIQUE,
    receipt_item_id BIGINT NULL UNIQUE,
    supplier_id BIGINT NOT NULL,
    ras VARCHAR(80) NULL,
    gender VARCHAR(10) NOT NULL,
    birth_date DATE NULL,
    estimated_age_months INT NULL,
    entry_date DATE NOT NULL,
    entry_weight_kg DECIMAL(12,3) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'AKTIF',
    health_status VARCHAR(20) NOT NULL DEFAULT 'SEHAT',
    sale_readiness VARCHAR(20) NOT NULL DEFAULT 'KARANTINA',
    acquisition_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    physical_marks VARCHAR(300) NULL,
    notes VARCHAR(500) NULL,
    photo_attachment_id BIGINT NULL,
    death_date DATE NULL,
    death_reason VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 1,
    CONSTRAINT fk_goat_supplier FOREIGN KEY (supplier_id) REFERENCES parties(id),
    CONSTRAINT ck_goat_status CHECK (status IN ('AKTIF','TERJUAL','MATI','DIKEMBALIKAN_KE_SUPPLIER')),
    CONSTRAINT ck_goat_health CHECK (health_status IN ('SEHAT','SAKIT','PEMULIHAN')),
    CONSTRAINT ck_goat_readiness CHECK (sale_readiness IN ('KARANTINA','SIAP_JUAL')),
    CONSTRAINT ck_goat_weight CHECK (entry_weight_kg > 0),
    INDEX idx_goat_status (status, sale_readiness, health_status),
    INDEX idx_goat_supplier (supplier_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE receipt_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    receipt_id BIGINT NOT NULL,
    purchase_item_id BIGINT NOT NULL,
    goat_id BIGINT NOT NULL,
    weight_kg DECIMAL(12,3) NOT NULL,
    condition_note VARCHAR(300) NULL,
    pen_id BIGINT NULL,
    acquisition_cost DECIMAL(18,2) NOT NULL,
    CONSTRAINT fk_ri_receipt FOREIGN KEY (receipt_id) REFERENCES receipts(id),
    CONSTRAINT fk_ri_purchase_item FOREIGN KEY (purchase_item_id) REFERENCES purchase_items(id),
    CONSTRAINT fk_ri_goat FOREIGN KEY (goat_id) REFERENCES goats(id),
    CONSTRAINT fk_ri_pen FOREIGN KEY (pen_id) REFERENCES pens(id),
    INDEX idx_ri_receipt (receipt_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE pen_placements (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    goat_id BIGINT NOT NULL,
    pen_id BIGINT NOT NULL,
    entered_at DATETIME NOT NULL,
    exited_at DATETIME NULL,
    reason VARCHAR(300) NULL,
    created_by BIGINT NULL,
    CONSTRAINT fk_pp_goat FOREIGN KEY (goat_id) REFERENCES goats(id),
    CONSTRAINT fk_pp_pen FOREIGN KEY (pen_id) REFERENCES pens(id),
    INDEX idx_pp_goat (goat_id, exited_at),
    INDEX idx_pp_pen (pen_id, exited_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE weight_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    goat_id BIGINT NOT NULL,
    measured_at DATE NOT NULL,
    weight_kg DECIMAL(12,3) NOT NULL,
    user_id BIGINT NULL,
    notes VARCHAR(300) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wr_goat FOREIGN KEY (goat_id) REFERENCES goats(id),
    CONSTRAINT ck_wr_weight CHECK (weight_kg > 0),
    INDEX idx_wr_goat (goat_id, measured_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE health_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    goat_id BIGINT NOT NULL,
    occurred_at DATE NOT NULL,
    complaint VARCHAR(300) NULL,
    examination VARCHAR(500) NULL,
    action VARCHAR(500) NULL,
    medication VARCHAR(300) NULL,
    cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    follow_up_date DATE NULL,
    responsible VARCHAR(120) NULL,
    recorder_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_hr_goat FOREIGN KEY (goat_id) REFERENCES goats(id),
    INDEX idx_hr_goat (goat_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE feed_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    min_stock DECIMAL(12,3) NOT NULL DEFAULT 0,
    stock_qty DECIMAL(12,3) NOT NULL DEFAULT 0,
    avg_cost DECIMAL(18,4) NOT NULL DEFAULT 0,
    active TINYINT(1) NOT NULL DEFAULT 1,
    CONSTRAINT ck_feed_stock CHECK (stock_qty >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE feed_movements (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    feed_item_id BIGINT NOT NULL,
    movement_type VARCHAR(20) NOT NULL,
    movement_date DATE NOT NULL,
    qty DECIMAL(12,3) NOT NULL,
    unit_cost DECIMAL(18,4) NOT NULL,
    total_cost DECIMAL(18,2) NOT NULL,
    pen_id BIGINT NULL,
    goat_id BIGINT NULL,
    service_start DATE NULL,
    service_end DATE NULL,
    notes VARCHAR(300) NULL,
    created_by BIGINT NULL,
    reversed_by_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_fm_item FOREIGN KEY (feed_item_id) REFERENCES feed_items(id),
    CONSTRAINT fk_fm_pen FOREIGN KEY (pen_id) REFERENCES pens(id),
    CONSTRAINT fk_fm_goat FOREIGN KEY (goat_id) REFERENCES goats(id),
    CONSTRAINT ck_fm_type CHECK (movement_type IN ('MASUK','PEMAKAIAN')),
    CONSTRAINT ck_fm_qty CHECK (qty > 0),
    INDEX idx_fm_item (feed_item_id, movement_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE expense_categories (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    treatment VARCHAR(20) NOT NULL,
    allocatable TINYINT(1) NOT NULL DEFAULT 0,
    active TINYINT(1) NOT NULL DEFAULT 1,
    CONSTRAINT ck_ec_treatment CHECK (treatment IN ('OPERASIONAL','PENJUALAN','ASET','PEROLEHAN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE expenses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    doc_no VARCHAR(40) NOT NULL UNIQUE,
    category_id BIGINT NOT NULL,
    expense_date DATE NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    party_id BIGINT NULL,
    purpose VARCHAR(300) NULL,
    notes VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    service_start DATE NULL,
    service_end DATE NULL,
    scope_pen_id BIGINT NULL,
    allocatable TINYINT(1) NOT NULL DEFAULT 0,
    direct_goat_id BIGINT NULL,
    cash_account_id BIGINT NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    posted_at DATETIME NULL,
    reversed_by_id BIGINT NULL,
    CONSTRAINT fk_exp_category FOREIGN KEY (category_id) REFERENCES expense_categories(id),
    CONSTRAINT fk_exp_party FOREIGN KEY (party_id) REFERENCES parties(id),
    CONSTRAINT fk_exp_pen FOREIGN KEY (scope_pen_id) REFERENCES pens(id),
    CONSTRAINT fk_exp_goat FOREIGN KEY (direct_goat_id) REFERENCES goats(id),
    CONSTRAINT ck_exp_status CHECK (status IN ('DRAFT','POSTED','REVERSED')),
    CONSTRAINT ck_exp_amount CHECK (amount > 0),
    INDEX idx_exp_date (expense_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE cash_accounts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    account_type VARCHAR(20) NOT NULL DEFAULT 'KAS',
    opening_balance DECIMAL(18,2) NOT NULL DEFAULT 0,
    active TINYINT(1) NOT NULL DEFAULT 1,
    CONSTRAINT ck_ca_type CHECK (account_type IN ('KAS','BANK'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    doc_no VARCHAR(40) NOT NULL UNIQUE,
    direction VARCHAR(10) NOT NULL,
    party_id BIGINT NULL,
    payment_date DATE NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    method VARCHAR(40) NOT NULL DEFAULT 'TUNAI',
    cash_account_id BIGINT NOT NULL,
    reference_no VARCHAR(100) NULL,
    source_type VARCHAR(30) NOT NULL,
    source_id BIGINT NULL,
    payment_type VARCHAR(20) NOT NULL DEFAULT 'PELUNASAN',
    status VARCHAR(20) NOT NULL DEFAULT 'POSTED',
    notes VARCHAR(500) NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reversed_by_id BIGINT NULL,
    CONSTRAINT fk_pay_party FOREIGN KEY (party_id) REFERENCES parties(id),
    CONSTRAINT fk_pay_account FOREIGN KEY (cash_account_id) REFERENCES cash_accounts(id),
    CONSTRAINT ck_pay_direction CHECK (direction IN ('IN','OUT')),
    CONSTRAINT ck_pay_amount CHECK (amount > 0),
    CONSTRAINT ck_pay_status CHECK (status IN ('POSTED','REVERSED')),
    INDEX idx_pay_date (payment_date),
    INDEX idx_pay_source (source_type, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sales (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    doc_no VARCHAR(40) NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    sale_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    payment_status VARCHAR(20) NOT NULL DEFAULT 'BELUM_BAYAR',
    total_gross DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_discount DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_net DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_hpp DECIMAL(18,2) NOT NULL DEFAULT 0,
    notes VARCHAR(500) NULL,
    approved_by BIGINT NULL,
    approval_reason VARCHAR(500) NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at DATETIME NULL,
    version INT NOT NULL DEFAULT 1,
    CONSTRAINT fk_sale_customer FOREIGN KEY (customer_id) REFERENCES parties(id),
    CONSTRAINT ck_sale_status CHECK (status IN ('DRAFT','DIKONFIRMASI','SELESAI','DIBATALKAN')),
    CONSTRAINT ck_sale_pay CHECK (payment_status IN ('BELUM_BAYAR','SEBAGIAN','LUNAS')),
    INDEX idx_sale_date (sale_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sale_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sale_id BIGINT NOT NULL,
    goat_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    pricing_method VARCHAR(10) NOT NULL,
    weight_kg DECIMAL(12,3) NULL,
    rate DECIMAL(18,2) NOT NULL,
    gross DECIMAL(18,2) NOT NULL,
    discount DECIMAL(18,2) NOT NULL DEFAULT 0,
    document_discount DECIMAL(18,2) NOT NULL DEFAULT 0,
    net DECIMAL(18,2) NOT NULL,
    hpp DECIMAL(18,2) NOT NULL DEFAULT 0,
    CONSTRAINT fk_si_sale FOREIGN KEY (sale_id) REFERENCES sales(id),
    CONSTRAINT fk_si_goat FOREIGN KEY (goat_id) REFERENCES goats(id),
    UNIQUE KEY uk_si_line (sale_id, line_no),
    UNIQUE KEY uk_si_goat (sale_id, goat_id),
    CONSTRAINT ck_si_method CHECK (pricing_method IN ('PER_EKOR','PER_KG')),
    CONSTRAINT ck_si_disc CHECK (discount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payment_allocations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    sale_id BIGINT NULL,
    purchase_id BIGINT NULL,
    amount DECIMAL(18,2) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pa_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    CONSTRAINT fk_pa_sale FOREIGN KEY (sale_id) REFERENCES sales(id),
    CONSTRAINT fk_pa_purchase FOREIGN KEY (purchase_id) REFERENCES purchases(id),
    UNIQUE KEY uk_pa_payment (payment_id),
    CONSTRAINT ck_pa_ref CHECK ((sale_id IS NOT NULL) <> (purchase_id IS NOT NULL)),
    CONSTRAINT ck_pa_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sale_approvals (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sale_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    requested_by BIGINT NULL,
    approved_by BIGINT NULL,
    approved_at DATETIME NULL,
    draft_version INT NOT NULL,
    CONSTRAINT fk_sa_sale FOREIGN KEY (sale_id) REFERENCES sales(id),
    INDEX idx_sa_sale (sale_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Kunci unik penjualan efektif per kambing (mencegah dua penjualan aktif).
CREATE TABLE goat_sale_locks (
    goat_id BIGINT PRIMARY KEY,
    sale_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_gsl_goat FOREIGN KEY (goat_id) REFERENCES goats(id),
    CONSTRAINT fk_gsl_sale FOREIGN KEY (sale_id) REFERENCES sales(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE cost_sources (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    source_type VARCHAR(30) NOT NULL,
    source_id BIGINT NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    service_start DATE NULL,
    service_end DATE NULL,
    scope_pen_id BIGINT NULL,
    direct_goat_id BIGINT NULL,
    allocatable TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cs_pen FOREIGN KEY (scope_pen_id) REFERENCES pens(id),
    CONSTRAINT fk_cs_goat FOREIGN KEY (direct_goat_id) REFERENCES goats(id),
    UNIQUE KEY uk_cs_source (source_type, source_id),
    CONSTRAINT ck_cs_amount CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE allocation_batches (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    cost_source_id BIGINT NOT NULL,
    version INT NOT NULL,
    method VARCHAR(30) NOT NULL DEFAULT 'HARI_KEBERADAAN',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finalized_at DATETIME NULL,
    reversed_by_id BIGINT NULL,
    CONSTRAINT fk_ab_source FOREIGN KEY (cost_source_id) REFERENCES cost_sources(id),
    UNIQUE KEY uk_ab_version (cost_source_id, version),
    CONSTRAINT ck_ab_status CHECK (status IN ('DRAFT','POSTED','REVERSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE cost_allocations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    goat_id BIGINT NOT NULL,
    days INT NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    CONSTRAINT fk_ca_batch FOREIGN KEY (batch_id) REFERENCES allocation_batches(id),
    CONSTRAINT fk_ca_goat FOREIGN KEY (goat_id) REFERENCES goats(id),
    UNIQUE KEY uk_ca_goat (batch_id, goat_id),
    INDEX idx_ca_goat (goat_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE goat_profit_snapshots (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    goat_id BIGINT NOT NULL,
    sale_item_id BIGINT NULL,
    version INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SEMENTARA',
    revenue DECIMAL(18,2) NOT NULL,
    hpp DECIMAL(18,2) NOT NULL,
    direct_cost DECIMAL(18,2) NOT NULL,
    allocated_cost DECIMAL(18,2) NOT NULL,
    profit DECIMAL(18,2) NOT NULL,
    margin_percent DECIMAL(9,2) NULL,
    rule_version VARCHAR(30) NOT NULL DEFAULT 'PRD-1.1',
    finalized_at DATETIME NULL,
    finalized_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_gps_goat FOREIGN KEY (goat_id) REFERENCES goats(id),
    CONSTRAINT fk_gps_item FOREIGN KEY (sale_item_id) REFERENCES sale_items(id),
    UNIQUE KEY uk_gps_version (goat_id, version),
    CONSTRAINT ck_gps_status CHECK (status IN ('SEMENTARA','FINAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inventory_writeoffs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    doc_no VARCHAR(40) NOT NULL UNIQUE,
    goat_id BIGINT NOT NULL,
    writeoff_date DATE NOT NULL,
    value DECIMAL(18,2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wo_goat FOREIGN KEY (goat_id) REFERENCES goats(id),
    UNIQUE KEY uk_wo_goat (goat_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE accounts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    account_type VARCHAR(30) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE journal_entries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    doc_no VARCHAR(40) NOT NULL UNIQUE,
    entry_date DATE NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id BIGINT NOT NULL,
    event_key VARCHAR(80) NOT NULL,
    description VARCHAR(300) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'POSTED',
    reversal_of_id BIGINT NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_je_event (event_key),
    INDEX idx_je_date (entry_date),
    CONSTRAINT ck_je_status CHECK (status IN ('POSTED','REVERSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE journal_lines (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    entry_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    debit DECIMAL(18,2) NOT NULL DEFAULT 0,
    credit DECIMAL(18,2) NOT NULL DEFAULT 0,
    description VARCHAR(300) NULL,
    CONSTRAINT fk_jl_entry FOREIGN KEY (entry_id) REFERENCES journal_entries(id),
    CONSTRAINT fk_jl_account FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT ck_jl_amount CHECK (debit >= 0 AND credit >= 0 AND (debit = 0 OR credit = 0)),
    INDEX idx_jl_account (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE account_transfers (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    doc_no VARCHAR(40) NOT NULL UNIQUE,
    transfer_date DATE NOT NULL,
    source_account_id BIGINT NOT NULL,
    destination_account_id BIGINT NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    notes VARCHAR(300) NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_at_source FOREIGN KEY (source_account_id) REFERENCES cash_accounts(id),
    CONSTRAINT fk_at_dest FOREIGN KEY (destination_account_id) REFERENCES cash_accounts(id),
    CONSTRAINT ck_at_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE cash_entries (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    doc_no VARCHAR(40) NOT NULL UNIQUE,
    entry_type VARCHAR(20) NOT NULL,
    entry_date DATE NOT NULL,
    cash_account_id BIGINT NOT NULL,
    amount DECIMAL(18,2) NOT NULL,
    notes VARCHAR(300) NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ce_account FOREIGN KEY (cash_account_id) REFERENCES cash_accounts(id),
    CONSTRAINT ck_ce_type CHECK (entry_type IN ('MODAL_MASUK','MODAL_KELUAR')),
    CONSTRAINT ck_ce_amount CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE accounting_periods (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    period_code CHAR(7) NOT NULL UNIQUE,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    closed_by BIGINT NULL,
    closed_at DATETIME NULL,
    reopened_by BIGINT NULL,
    reopened_at DATETIME NULL,
    reopen_reason VARCHAR(500) NULL,
    CONSTRAINT ck_ap_status CHECK (status IN ('OPEN','CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE attachments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    original_name VARCHAR(200) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    storage_key VARCHAR(200) NOT NULL UNIQUE,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE app_settings (
    setting_key VARCHAR(80) PRIMARY KEY,
    setting_value VARCHAR(500) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE processed_requests (
    request_id CHAR(36) PRIMARY KEY,
    actor_id BIGINT NULL,
    command_type VARCHAR(60) NOT NULL,
    result_reference VARCHAR(100) NULL,
    response_json TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE backup_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    schema_version INT NOT NULL,
    location VARCHAR(300) NOT NULL,
    result VARCHAR(30) NOT NULL,
    created_by BIGINT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE doc_sequences (
    prefix VARCHAR(10) NOT NULL,
    period CHAR(6) NOT NULL,
    last_no INT NOT NULL DEFAULT 0,
    PRIMARY KEY (prefix, period)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

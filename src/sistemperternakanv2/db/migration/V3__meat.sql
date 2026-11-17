-- V3: pemotongan kambing, persediaan daging, dan penjualan daging per kg.

-- Status kambing mendapat nilai baru DIPOTONG.
ALTER TABLE goats DROP CHECK ck_goat_status;
ALTER TABLE goats ADD CONSTRAINT ck_goat_status
    CHECK (status IN ('AKTIF','TERJUAL','MATI','DIKEMBALIKAN_KE_SUPPLIER','DIPOTONG'));

CREATE TABLE slaughter_orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    doc_no VARCHAR(40) NOT NULL UNIQUE,
    goat_id BIGINT NOT NULL,
    slaughter_date DATE NOT NULL,
    live_weight_kg DECIMAL(12,3) NOT NULL,
    carcass_weight_kg DECIMAL(12,3) NOT NULL,
    meat_weight_kg DECIMAL(12,3) NOT NULL,
    bone_weight_kg DECIMAL(12,3) NOT NULL DEFAULT 0,
    offal_weight_kg DECIMAL(12,3) NULL,
    dressing_pct DECIMAL(6,2) NOT NULL DEFAULT 0,
    meat_yield_pct DECIMAL(6,2) NOT NULL DEFAULT 0,
    bone_pct DECIMAL(6,2) NOT NULL DEFAULT 0,
    goat_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    processing_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_cost DECIMAL(18,2) NOT NULL DEFAULT 0,
    hpp_per_kg DECIMAL(18,4) NOT NULL DEFAULT 0,
    cash_account_id BIGINT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    notes VARCHAR(500) NULL,
    created_by BIGINT NULL,
    confirmed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 1,
    active_goat_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status <> 'DIBATALKAN' THEN goat_id ELSE NULL END) STORED,
    CONSTRAINT fk_so_goat FOREIGN KEY (goat_id) REFERENCES goats(id),
    CONSTRAINT fk_so_cash FOREIGN KEY (cash_account_id) REFERENCES cash_accounts(id),
    CONSTRAINT ck_so_status CHECK (status IN ('DRAFT','POSTED','DIBATALKAN')),
    CONSTRAINT ck_so_live CHECK (live_weight_kg > 0),
    CONSTRAINT ck_so_carcass CHECK (carcass_weight_kg > 0),
    CONSTRAINT ck_so_meat CHECK (meat_weight_kg > 0),
    CONSTRAINT ck_so_bone CHECK (bone_weight_kg >= 0),
    UNIQUE KEY uk_so_active_goat (active_goat_id),
    INDEX idx_so_date (slaughter_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE meat_batches (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_no VARCHAR(50) NOT NULL UNIQUE,
    slaughter_id BIGINT NOT NULL UNIQUE,
    goat_id BIGINT NOT NULL,
    qty_in_kg DECIMAL(12,3) NOT NULL,
    qty_remaining_kg DECIMAL(12,3) NOT NULL,
    hpp_per_kg DECIMAL(18,4) NOT NULL,
    received_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mb_slaughter FOREIGN KEY (slaughter_id) REFERENCES slaughter_orders(id),
    CONSTRAINT fk_mb_goat FOREIGN KEY (goat_id) REFERENCES goats(id),
    CONSTRAINT ck_mb_qty CHECK (qty_in_kg > 0 AND qty_remaining_kg >= 0),
    CONSTRAINT ck_mb_status CHECK (status IN ('OPEN','CLOSED')),
    INDEX idx_mb_date (received_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE meat_movements (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    movement_type VARCHAR(20) NOT NULL,
    movement_date DATE NOT NULL,
    qty_kg DECIMAL(12,3) NOT NULL,
    unit_cost DECIMAL(18,4) NOT NULL,
    total_cost DECIMAL(18,2) NOT NULL,
    sale_id BIGINT NULL,
    notes VARCHAR(300) NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mm_batch FOREIGN KEY (batch_id) REFERENCES meat_batches(id),
    CONSTRAINT fk_mm_sale FOREIGN KEY (sale_id) REFERENCES sales(id),
    CONSTRAINT ck_mm_type CHECK (movement_type IN ('MASUK','PENJUALAN','SUSUT')),
    CONSTRAINT ck_mm_qty CHECK (qty_kg > 0),
    INDEX idx_mm_batch (batch_id, movement_date),
    INDEX idx_mm_sale (sale_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Penjualan dapat berisi kambing hidup atau daging, tidak dicampur.
ALTER TABLE sales ADD COLUMN sale_type VARCHAR(15) NOT NULL DEFAULT 'KAMBING';
ALTER TABLE sales ADD CONSTRAINT ck_sale_type CHECK (sale_type IN ('KAMBING','DAGING'));

ALTER TABLE sale_items MODIFY goat_id BIGINT NULL;
ALTER TABLE sale_items ADD COLUMN item_type VARCHAR(10) NOT NULL DEFAULT 'KAMBING';
ALTER TABLE sale_items ADD COLUMN meat_batch_id BIGINT NULL;
ALTER TABLE sale_items ADD CONSTRAINT fk_si_meat_batch FOREIGN KEY (meat_batch_id) REFERENCES meat_batches(id);
ALTER TABLE sale_items ADD CONSTRAINT ck_si_item_type CHECK (item_type IN ('KAMBING','DAGING'));
ALTER TABLE sale_items ADD CONSTRAINT ck_si_ref CHECK (
    (item_type = 'KAMBING' AND goat_id IS NOT NULL AND meat_batch_id IS NULL)
    OR (item_type = 'DAGING' AND goat_id IS NULL AND meat_batch_id IS NOT NULL));
ALTER TABLE sale_items ADD UNIQUE KEY uk_si_meat_batch (sale_id, meat_batch_id);

-- Satu baris per penjualan efektif, baik kambing hidup maupun daging.
CREATE OR REPLACE VIEW v_goat_sale_items AS
SELECT si.id AS sale_item_id, si.sale_id, si.goat_id, si.net, si.hpp,
       s.sale_date, s.doc_no AS sale_doc_no, 'KAMBING' AS sale_type
FROM sale_items si JOIN sales s ON s.id = si.sale_id
WHERE si.goat_id IS NOT NULL AND s.status IN ('DIKONFIRMASI','SELESAI')
UNION ALL
SELECT si.id, si.sale_id, mb.goat_id, si.net, si.hpp,
       s.sale_date, s.doc_no, 'DAGING'
FROM sale_items si JOIN sales s ON s.id = si.sale_id
JOIN meat_batches mb ON mb.id = si.meat_batch_id
WHERE si.meat_batch_id IS NOT NULL AND s.status IN ('DIKONFIRMASI','SELESAI');

INSERT INTO permissions (code, description) VALUES
 ('slaughter.manage','Mencatat dan memposting pemotongan kambing'),
 ('meat.view','Melihat persediaan daging');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('slaughter.manage','meat.view')
WHERE r.code = 'OWNER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('slaughter.manage','meat.view')
WHERE r.code = 'ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'meat.view'
WHERE r.code = 'PETUGAS';

INSERT INTO accounts (code, name, account_type) VALUES
 ('1303','Persediaan Daging','ASET'),
 ('4102','Penjualan Daging','PENDAPATAN'),
 ('5102','Harga Pokok Penjualan Daging','BEBAN');

INSERT INTO app_settings (setting_key, setting_value) VALUES
 ('meat.price_per_kg','0');

-- V7: multi-cabang (Pusat, Cikarang) dengan data lama masuk Pusat.
-- Setiap cabang mandiri: kandang, pembelian, pemotongan, daging, pakan,
-- penjualan, pembayaran, pengeluaran, dan jurnal sendiri.

CREATE TABLE branches (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    branch_type VARCHAR(20) NOT NULL DEFAULT 'CABANG',
    address VARCHAR(300) NULL,
    phone VARCHAR(40) NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version INT NOT NULL DEFAULT 1,
    CONSTRAINT ck_branch_type CHECK (branch_type IN ('PUSAT','CABANG','RPH')),
    INDEX idx_branch_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO branches (code, name, branch_type) VALUES
 ('PUSAT', 'Pusat', 'PUSAT'),
 ('CIKARANG', 'Cikarang', 'CABANG');

CREATE TABLE user_branches (
    user_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, branch_id),
    CONSTRAINT fk_ub_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_ub_branch FOREIGN KEY (branch_id) REFERENCES branches(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE pens ADD COLUMN branch_id BIGINT NULL AFTER id;
ALTER TABLE goats ADD COLUMN branch_id BIGINT NULL AFTER id;
ALTER TABLE purchases ADD COLUMN branch_id BIGINT NULL AFTER id;
ALTER TABLE receipts ADD COLUMN branch_id BIGINT NULL AFTER id;
ALTER TABLE sales ADD COLUMN branch_id BIGINT NULL AFTER id;
ALTER TABLE expenses ADD COLUMN branch_id BIGINT NULL AFTER id;
ALTER TABLE payments ADD COLUMN branch_id BIGINT NULL AFTER id;
ALTER TABLE feed_items ADD COLUMN branch_id BIGINT NULL AFTER id;
ALTER TABLE slaughter_orders ADD COLUMN branch_id BIGINT NULL AFTER id;
ALTER TABLE meat_batches ADD COLUMN branch_id BIGINT NULL AFTER id;
ALTER TABLE journal_entries ADD COLUMN branch_id BIGINT NULL AFTER id;

UPDATE pens SET branch_id = (SELECT id FROM branches WHERE code = 'PUSAT');
UPDATE goats SET branch_id = (SELECT id FROM branches WHERE code = 'PUSAT');
UPDATE purchases SET branch_id = (SELECT id FROM branches WHERE code = 'PUSAT');
UPDATE receipts SET branch_id = (SELECT id FROM branches WHERE code = 'PUSAT');
UPDATE sales SET branch_id = (SELECT id FROM branches WHERE code = 'PUSAT');
UPDATE expenses SET branch_id = (SELECT id FROM branches WHERE code = 'PUSAT');
UPDATE payments SET branch_id = (SELECT id FROM branches WHERE code = 'PUSAT');
UPDATE feed_items SET branch_id = (SELECT id FROM branches WHERE code = 'PUSAT');
UPDATE slaughter_orders SET branch_id = (SELECT id FROM branches WHERE code = 'PUSAT');
UPDATE meat_batches SET branch_id = (SELECT id FROM branches WHERE code = 'PUSAT');
UPDATE journal_entries SET branch_id = (SELECT id FROM branches WHERE code = 'PUSAT');

ALTER TABLE pens MODIFY branch_id BIGINT NOT NULL;
ALTER TABLE goats MODIFY branch_id BIGINT NOT NULL;
ALTER TABLE purchases MODIFY branch_id BIGINT NOT NULL;
ALTER TABLE receipts MODIFY branch_id BIGINT NOT NULL;
ALTER TABLE sales MODIFY branch_id BIGINT NOT NULL;
ALTER TABLE expenses MODIFY branch_id BIGINT NOT NULL;
ALTER TABLE payments MODIFY branch_id BIGINT NOT NULL;
ALTER TABLE feed_items MODIFY branch_id BIGINT NOT NULL;
ALTER TABLE slaughter_orders MODIFY branch_id BIGINT NOT NULL;
ALTER TABLE meat_batches MODIFY branch_id BIGINT NOT NULL;
ALTER TABLE journal_entries MODIFY branch_id BIGINT NOT NULL;

ALTER TABLE pens ADD CONSTRAINT fk_pen_branch FOREIGN KEY (branch_id) REFERENCES branches(id);
ALTER TABLE goats ADD CONSTRAINT fk_goat_branch FOREIGN KEY (branch_id) REFERENCES branches(id);
ALTER TABLE purchases ADD CONSTRAINT fk_purchase_branch FOREIGN KEY (branch_id) REFERENCES branches(id);
ALTER TABLE receipts ADD CONSTRAINT fk_receipt_branch FOREIGN KEY (branch_id) REFERENCES branches(id);
ALTER TABLE sales ADD CONSTRAINT fk_sale_branch FOREIGN KEY (branch_id) REFERENCES branches(id);
ALTER TABLE expenses ADD CONSTRAINT fk_expense_branch FOREIGN KEY (branch_id) REFERENCES branches(id);
ALTER TABLE payments ADD CONSTRAINT fk_payment_branch FOREIGN KEY (branch_id) REFERENCES branches(id);
ALTER TABLE feed_items ADD CONSTRAINT fk_feed_branch FOREIGN KEY (branch_id) REFERENCES branches(id);
ALTER TABLE slaughter_orders ADD CONSTRAINT fk_slaughter_branch FOREIGN KEY (branch_id) REFERENCES branches(id);
ALTER TABLE meat_batches ADD CONSTRAINT fk_meat_branch FOREIGN KEY (branch_id) REFERENCES branches(id);
ALTER TABLE journal_entries ADD CONSTRAINT fk_journal_branch FOREIGN KEY (branch_id) REFERENCES branches(id);

ALTER TABLE pens DROP INDEX code;
ALTER TABLE pens ADD UNIQUE KEY uk_pen_branch_code (branch_id, code);
ALTER TABLE feed_items DROP INDEX code;
ALTER TABLE feed_items ADD UNIQUE KEY uk_feed_branch_code (branch_id, code);

CREATE INDEX idx_purchase_branch ON purchases (branch_id, purchase_date);
CREATE INDEX idx_receipt_branch ON receipts (branch_id, receipt_date);
CREATE INDEX idx_sale_branch ON sales (branch_id, sale_date);
CREATE INDEX idx_expense_branch ON expenses (branch_id, expense_date);
CREATE INDEX idx_payment_branch ON payments (branch_id, payment_date);
CREATE INDEX idx_slaughter_branch ON slaughter_orders (branch_id, slaughter_date);
CREATE INDEX idx_meat_branch ON meat_batches (branch_id, received_date);
CREATE INDEX idx_journal_branch ON journal_entries (branch_id, entry_date);
CREATE INDEX idx_goat_branch ON goats (branch_id, status);

INSERT INTO permissions (code, description) VALUES
 ('branch.manage', 'Mengelola cabang dan penugasan pengguna');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'branch.manage'
WHERE r.code = 'OWNER';

INSERT INTO user_branches (user_id, branch_id)
SELECT u.id, b.id FROM users u
JOIN roles r ON r.id = u.role_id
JOIN branches b ON b.code = 'PUSAT'
WHERE r.code <> 'OWNER';

-- V8: hapus tipe cabang RPH. Semua unit operasional adalah cabang biasa.
UPDATE branches SET branch_type = 'CABANG' WHERE branch_type = 'RPH';

ALTER TABLE branches DROP CHECK ck_branch_type;
ALTER TABLE branches ADD CONSTRAINT ck_branch_type CHECK (branch_type IN ('PUSAT','CABANG'));

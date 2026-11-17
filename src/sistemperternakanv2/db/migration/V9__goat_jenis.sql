-- V9: ganti kolom ras menjadi jenis ternak (Kambing/Domba).
UPDATE goats SET ras = CASE
    WHEN LOWER(TRIM(COALESCE(ras, ''))) IN ('domba', 'sheep') THEN 'Domba'
    ELSE 'Kambing'
END;

ALTER TABLE goats CHANGE COLUMN ras jenis VARCHAR(20) NOT NULL DEFAULT 'Kambing';
ALTER TABLE goats ADD CONSTRAINT ck_goat_jenis CHECK (jenis IN ('Kambing','Domba'));

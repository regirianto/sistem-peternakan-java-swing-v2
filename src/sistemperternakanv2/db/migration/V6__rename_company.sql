-- V6: ganti nama usaha menjadi Berkah Aamin Farm.
UPDATE app_settings SET setting_value = 'Berkah Aamin Farm' WHERE setting_key = 'company.name';
INSERT IGNORE INTO app_settings (setting_key, setting_value) VALUES ('company.name', 'Berkah Aamin Farm');

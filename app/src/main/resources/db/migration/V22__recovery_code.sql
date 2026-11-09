-- V22: „Am uitat parola” cu codul de recuperare dat la crearea contului (în locul codului pe email din V21: SES e în
-- sandbox, iar planul gratuit nu are SMS). Dacă omul pierde și codul, Adminul îi pune o parolă temporară, iar la intrare
-- aplicația îl obligă să-și aleagă alta.
ALTER TABLE users ADD COLUMN recovery_code_hash   VARCHAR(100);
ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT false;

DROP TABLE IF EXISTS password_reset_codes;

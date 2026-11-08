-- GDPR: dovada consimțământului la crearea contului (bifa „Accept Termenii și condițiile și Politica de confidențialitate”).
-- Conturile existente (seed, create de Primărie / Admin) rămân NULL.
ALTER TABLE users ADD COLUMN terms_accepted_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN terms_version VARCHAR(20);

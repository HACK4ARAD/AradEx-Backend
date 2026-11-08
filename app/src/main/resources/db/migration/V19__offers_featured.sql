-- V19: oferte „promovate” — apar primele în lista de oferte (înaintea sortării după distanță sau dată). Se setează din seed;
-- firma nu și le poate promova singură. Vezi docs/DATA-MODEL.md.
ALTER TABLE offers ADD COLUMN IF NOT EXISTS featured BOOLEAN NOT NULL DEFAULT false;

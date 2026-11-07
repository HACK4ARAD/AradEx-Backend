-- V16 (T17): ID-ul locului pe Google (Places API New), pentru rating și recenzii afișate prin backend.
-- `businesses.google_place_id` există din V6. La obiective ID-ul se caută automat pe Google după nume + coordonate
-- la prima cerere (Text Search, doar ID) și se salvează aici; Primăria îl poate corecta din panou.
ALTER TABLE places ADD COLUMN IF NOT EXISTS google_place_id VARCHAR(255);

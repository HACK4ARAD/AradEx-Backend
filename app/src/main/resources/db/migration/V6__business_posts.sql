-- V6 (T09): anunțurile firmelor + google_place_id pentru T17. Vezi docs/DATA-MODEL.md.
CREATE TABLE announcements (
    id          BIGSERIAL PRIMARY KEY,
    business_id BIGINT NOT NULL REFERENCES businesses(id) ON DELETE CASCADE,
    title       VARCHAR(120) NOT NULL,
    body        TEXT NOT NULL,
    image_url   VARCHAR(500),
    status      VARCHAR(10) NOT NULL DEFAULT 'PUBLISHED' CHECK (status IN ('DRAFT','PUBLISHED','HIDDEN')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_announcements_business ON announcements(business_id, created_at DESC);

ALTER TABLE businesses ADD COLUMN google_place_id VARCHAR(255);

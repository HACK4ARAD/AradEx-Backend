-- V3 (T05): obiective turistice + puncte de ghid virtual. Vezi docs/DATA-MODEL.md.
CREATE TABLE places (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(160) NOT NULL,
    slug            VARCHAR(160) NOT NULL UNIQUE,
    type            VARCHAR(20)  NOT NULL CHECK (type IN ('ATTRACTION','MUSEUM','VENUE','WINERY','NATURE','PARK','RESTAURANT','CAFE')),
    description     TEXT NOT NULL,
    address         VARCHAR(250),
    lat             DOUBLE PRECISION NOT NULL,
    lng             DOUBLE PRECISION NOT NULL,
    image_url       VARCHAR(500),
    image_credit    VARCHAR(250),
    opening_hours   TEXT,
    visit_minutes   INT NOT NULL DEFAULT 60,
    tags            VARCHAR(250),           -- csv: "istorie,familie"
    rating          NUMERIC(2,1),
    parking_hint    TEXT,
    exterior_only   BOOLEAN NOT NULL DEFAULT false,  -- Cetatea, Turnul de Apă: se vizitează doar din exterior
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE guide_points (
    id              BIGSERIAL PRIMARY KEY,
    place_id        BIGINT NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    position        INT    NOT NULL,
    title           VARCHAR(160) NOT NULL,
    text            TEXT   NOT NULL,
    image_url       VARCHAR(500),
    audio_url       VARCHAR(500),
    lat             DOUBLE PRECISION,
    lng             DOUBLE PRECISION,
    duration_sec    INT,
    UNIQUE (place_id, position)
);

CREATE INDEX idx_places_type ON places(type);

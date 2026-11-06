-- V4 (T06): evenimente + evenimente salvate. Vezi docs/DATA-MODEL.md.
CREATE TABLE events (
    id                    BIGSERIAL PRIMARY KEY,
    title                 VARCHAR(200) NOT NULL,
    description           TEXT NOT NULL,
    category              VARCHAR(20) NOT NULL CHECK (category IN ('CONCERT','THEATRE','FAMILY','EXHIBITION','SPORT','OUTDOOR','FAIR','FOOD')),
    organizer_type        VARCHAR(20) NOT NULL CHECK (organizer_type IN ('CITY','BUSINESS','ADMIN','EXTERNAL')),
    organizer_business_id BIGINT REFERENCES businesses(id),
    organizer_name        VARCHAR(160) NOT NULL,
    place_id              BIGINT REFERENCES places(id),
    location_name         VARCHAR(160) NOT NULL,
    address               VARCHAR(250),
    lat                   DOUBLE PRECISION NOT NULL,
    lng                   DOUBLE PRECISION NOT NULL,
    start_at              TIMESTAMPTZ NOT NULL,
    end_at                TIMESTAMPTZ,
    image_url             VARCHAR(500),
    ticket_type           VARCHAR(10) NOT NULL CHECK (ticket_type IN ('FREE','IN_APP','EXTERNAL')),
    price_from            NUMERIC(10,2),
    price_adult           NUMERIC(10,2),
    price_child           NUMERIC(10,2),
    external_url          VARCHAR(500),
    capacity              INT,
    participants_count    INT NOT NULL DEFAULT 0,
    saves_count           INT NOT NULL DEFAULT 0,
    status                VARCHAR(10) NOT NULL DEFAULT 'PUBLISHED' CHECK (status IN ('DRAFT','PUBLISHED','HIDDEN')),
    is_demo               BOOLEAN NOT NULL DEFAULT false,   -- true = eveniment FICTIV creat pentru demo
    created_by            BIGINT REFERENCES users(id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE saved_events (
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_id   BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, event_id)
);

CREATE INDEX idx_events_start ON events(start_at);
CREATE INDEX idx_events_category_start ON events(category, start_at);
CREATE INDEX idx_events_business ON events(organizer_business_id);
CREATE INDEX idx_events_status ON events(status);

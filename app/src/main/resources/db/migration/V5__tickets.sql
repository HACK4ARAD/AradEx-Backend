-- V5 (T07): bilete și înscrieri la evenimente + recenzii (tabela pentru T07 partea 2). Vezi docs/DATA-MODEL.md.
CREATE TABLE tickets (
    id          BIGSERIAL PRIMARY KEY,
    event_id    BIGINT NOT NULL REFERENCES events(id),
    user_id     BIGINT NOT NULL REFERENCES users(id),
    qty_adult   INT NOT NULL DEFAULT 0 CHECK (qty_adult >= 0),
    qty_child   INT NOT NULL DEFAULT 0 CHECK (qty_child >= 0),
    total       NUMERIC(10,2) NOT NULL DEFAULT 0,
    code        VARCHAR(20) NOT NULL UNIQUE,          -- AX-T{eventId}-XXXXXX; QR-ul conține exact codul
    status      VARCHAR(10) NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('CONFIRMED','CANCELLED','USED')),
    email       VARCHAR(190) NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (qty_adult + qty_child > 0)
);

CREATE INDEX idx_tickets_user ON tickets(user_id);
CREATE INDEX idx_tickets_event ON tickets(event_id);

CREATE TABLE reviews (
    id          BIGSERIAL PRIMARY KEY,
    event_id    BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    stars       INT NOT NULL CHECK (stars BETWEEN 1 AND 5),
    text        VARCHAR(600),
    status      VARCHAR(10) NOT NULL DEFAULT 'PUBLISHED' CHECK (status IN ('PUBLISHED','HIDDEN')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (event_id, user_id)
);

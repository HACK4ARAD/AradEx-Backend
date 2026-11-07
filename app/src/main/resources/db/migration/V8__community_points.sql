-- V8 (T11): acțiuni comunitare, înscrieri, tranzacții de puncte. Vezi docs/DATA-MODEL.md.
-- users.points_balance (V2) = suma tranzacțiilor; îl scrie doar PointsService, în aceeași tranzacție DB.
CREATE TABLE community_actions (
    id                BIGSERIAL PRIMARY KEY,
    title             VARCHAR(160) NOT NULL,
    description       TEXT         NOT NULL,
    category          VARCHAR(20)  NOT NULL CHECK (category IN ('ENVIRONMENT','HEALTH','SOCIAL','CULTURE')),
    organizer         VARCHAR(120),                       -- „Partener / direcție” din design (ex. Direcția Spații Verzi)
    start_at          TIMESTAMPTZ  NOT NULL,
    end_at            TIMESTAMPTZ  NOT NULL,
    location_name     VARCHAR(200) NOT NULL,
    address           VARCHAR(250),
    lat               DOUBLE PRECISION NOT NULL,
    lng               DOUBLE PRECISION NOT NULL,
    points_reward     INT          NOT NULL CHECK (points_reward > 0),
    capacity          INT          NOT NULL CHECK (capacity > 0),
    registered_count  INT          NOT NULL DEFAULT 0,
    confirmed_count   INT          NOT NULL DEFAULT 0,
    image_url         VARCHAR(500),
    image_credit      VARCHAR(250),
    status            VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED' CHECK (status IN ('DRAFT','PUBLISHED','CANCELLED','DONE')),
    created_by        BIGINT       NOT NULL REFERENCES users(id),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (end_at > start_at)
);

CREATE TABLE community_registrations (
    id             BIGSERIAL PRIMARY KEY,
    action_id      BIGINT      NOT NULL REFERENCES community_actions(id) ON DELETE CASCADE,
    user_id        BIGINT      NOT NULL REFERENCES users(id),
    code           VARCHAR(40) NOT NULL UNIQUE,
    status         VARCHAR(20) NOT NULL DEFAULT 'REGISTERED' CHECK (status IN ('REGISTERED','CONFIRMED','CANCELLED','NO_SHOW')),
    registered_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_at   TIMESTAMPTZ,
    confirmed_by   BIGINT      REFERENCES users(id),
    UNIQUE (action_id, user_id)
);

CREATE TABLE points_transactions (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id),
    amount      INT         NOT NULL CHECK (amount <> 0),
    type        VARCHAR(20) NOT NULL CHECK (type IN ('EARN_ACTION','SPEND_OFFER','ADJUST')),
    ref_type    VARCHAR(20) CHECK (ref_type IN ('ACTION','OFFER','ADMIN')),
    ref_id      BIGINT,
    note        VARCHAR(250),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_actions_start ON community_actions(status, start_at);
CREATE INDEX idx_regs_action ON community_registrations(action_id, status);
CREATE INDEX idx_regs_user ON community_registrations(user_id);
CREATE INDEX idx_points_user ON points_transactions(user_id, created_at);
CREATE INDEX idx_points_created ON points_transactions(created_at);
-- un singur câștig per acțiune per utilizator, chiar dacă două confirmări ajung simultan
CREATE UNIQUE INDEX uq_points_earn_action ON points_transactions(user_id, ref_id) WHERE type = 'EARN_ACTION' AND ref_type = 'ACTION' AND ref_id IS NOT NULL;

-- T13 · Planificatorul de itinerariu: itinerariile salvate (POST /itineraries), deschise după id sau după codul de partajare.
-- Planul rezerva V9, dar producția e deja la V15: pe o bază nouă V9 ar rula înaintea lui V11–V15 (out-of-order nu ajută pe o
-- bază existentă la V15, unde ar intra după ele, iar ordinea ar diferi între medii). De aceea V16, primul număr liber.
-- Opririle stau ca JSON (lista `ItineraryStop` din API): se citesc și se scriu mereu întregi, nu se caută după ele.

CREATE TABLE itineraries (
    id             BIGSERIAL     PRIMARY KEY,
    share_code     VARCHAR(16)   NOT NULL UNIQUE,
    user_id        BIGINT        REFERENCES users(id) ON DELETE SET NULL,   -- NULL = salvat fără cont
    title          VARCHAR(160)  NOT NULL,
    summary        TEXT,
    ask            VARCHAR(300),
    date           DATE          NOT NULL,
    days           INT           NOT NULL DEFAULT 1 CHECK (days BETWEEN 1 AND 2),
    transport      VARCHAR(10)   NOT NULL CHECK (transport IN ('WALK','CAR','PUBLIC')),
    start_time     VARCHAR(5),
    total_minutes  INT           NOT NULL DEFAULT 0,
    total_km       NUMERIC(6,1)  NOT NULL DEFAULT 0,
    offline        BOOLEAN       NOT NULL DEFAULT false,
    stops          JSONB         NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX itineraries_user_created ON itineraries (user_id, created_at DESC);

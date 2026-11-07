-- V7 (T10): ofertele firmelor și codurile de revendicare. Vezi docs/DATA-MODEL.md.
CREATE TABLE offers (
    id                BIGSERIAL PRIMARY KEY,
    business_id       BIGINT NOT NULL REFERENCES businesses(id),
    title             VARCHAR(120) NOT NULL,
    description       TEXT NOT NULL,
    discount_label    VARCHAR(30) NOT NULL,               -- '-20%', '2+1', 'Gratuit', '40 p'
    category          VARCHAR(12) NOT NULL DEFAULT 'OTHER' CHECK (category IN ('CAFE','RESTAURANT','LOCAL','EVENT','OTHER')),
    local_product     BOOLEAN NOT NULL DEFAULT false,      -- „Făcut în Arad”
    valid_from        DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_until       DATE,                                -- NULL = permanentă
    points_price      INT CHECK (points_price IS NULL OR points_price > 0),
    image_url         VARCHAR(500),
    status            VARCHAR(10) NOT NULL DEFAULT 'PUBLISHED' CHECK (status IN ('DRAFT','PUBLISHED','HIDDEN')),
    views_count       INT NOT NULL DEFAULT 0,
    redemptions_count INT NOT NULL DEFAULT 0,
    used_count        INT NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (valid_until IS NULL OR valid_until >= valid_from)
);

CREATE TABLE offer_redemptions (
    id              BIGSERIAL PRIMARY KEY,
    offer_id        BIGINT NOT NULL REFERENCES offers(id),
    user_id         BIGINT NOT NULL REFERENCES users(id),
    code            VARCHAR(24) NOT NULL UNIQUE,           -- AX-O{offerId}-XXXXXX
    points_spent    INT NOT NULL DEFAULT 0,
    status          VARCHAR(10) NOT NULL DEFAULT 'ISSUED' CHECK (status IN ('ISSUED','USED','CANCELLED')),
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    used_at         TIMESTAMPTZ,
    used_by_user_id BIGINT REFERENCES users(id)
);

CREATE INDEX idx_offers_business ON offers(business_id);
CREATE INDEX idx_offers_status_valid ON offers(status, valid_until);
-- o singură revendicare activă (ISSUED) per utilizator per ofertă, garantat și la două cereri simultane
CREATE UNIQUE INDEX uq_redemptions_active ON offer_redemptions(offer_id, user_id) WHERE status = 'ISSUED';
CREATE INDEX idx_redemptions_user ON offer_redemptions(user_id, issued_at DESC);
CREATE INDEX idx_redemptions_offer ON offer_redemptions(offer_id, issued_at DESC);

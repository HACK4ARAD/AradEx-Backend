-- V2 (T03): utilizatori și firme. Vezi docs/DATA-MODEL.md.
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(190) NOT NULL UNIQUE,
    password_hash   VARCHAR(100) NOT NULL,
    display_name    VARCHAR(120) NOT NULL,
    role            VARCHAR(20)  NOT NULL CHECK (role IN ('VISITOR','LOCAL','BUSINESS','CITY_HALL','ADMIN')),
    locale          VARCHAR(5)   NOT NULL DEFAULT 'ro',
    points_balance  INT          NOT NULL DEFAULT 0,
    avatar_url      VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE businesses (
    id              BIGSERIAL PRIMARY KEY,
    owner_user_id   BIGINT NOT NULL UNIQUE REFERENCES users(id),
    name            VARCHAR(160) NOT NULL,
    category        VARCHAR(20)  NOT NULL CHECK (category IN ('CAFE','RESTAURANT','PRODUCER','WINERY','SHOP','OTHER')),
    description     TEXT,
    address         VARCHAR(250),
    lat             DOUBLE PRECISION,
    lng             DOUBLE PRECISION,
    phone           VARCHAR(40),
    website         VARCHAR(250),
    image_url       VARCHAR(500),
    verified        BOOLEAN NOT NULL DEFAULT false,
    views_count     INT     NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_role ON users(role);

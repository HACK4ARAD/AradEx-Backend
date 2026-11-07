-- T18 · Voluntari: conturi create doar de Primărie (cod de 6 cifre „prin SMS”), alocate pe acțiuni comunitare, cu mesaje
-- spre coordonator. Planul rezerva V10, dar între timp au intrat V11 (telefon, T21) și V12 (transport, T22): pe o bază nouă V10
-- ar rula înaintea lui V11, iar constrângerea „email sau telefon” din V11 ar pica la conturile de voluntar (n-au niciunul).

-- rolul nou în CHECK (V2) și voluntarii fără email / telefon în `users` (telefonul lor stă în `volunteers`, ca să nu poată
-- intra cu OTP-ul conturilor personale din T21)
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('VISITOR','LOCAL','BUSINESS','CITY_HALL','ADMIN','VOLUNTEER'));
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_email_or_phone;
ALTER TABLE users ADD CONSTRAINT users_email_or_phone CHECK (email IS NOT NULL OR phone IS NOT NULL OR role = 'VOLUNTEER');

-- un voluntar = un rând în `users` (rol VOLUNTEER, fără parolă) + rândul de aici
CREATE TABLE volunteers (
    user_id         BIGINT       PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    phone           VARCHAR(16)  NOT NULL,                       -- E.164, pentru SMS-ul cu codul (simulat)
    email           VARCHAR(190),
    login_code      VARCHAR(6)   NOT NULL UNIQUE CHECK (login_code ~ '^[0-9]{6}$'),
    one_time        BOOLEAN      NOT NULL DEFAULT false,         -- expiră după ultima acțiune alocată
    status          VARCHAR(12)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED')),
    coordinator_id  BIGINT       NOT NULL REFERENCES users(id),  -- un cont CITY_HALL (sau ADMIN)
    created_by      BIGINT       NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at   TIMESTAMPTZ
);
CREATE INDEX volunteers_status ON volunteers(status);

CREATE TABLE volunteer_actions (
    user_id    BIGINT NOT NULL REFERENCES volunteers(user_id) ON DELETE CASCADE,
    action_id  BIGINT NOT NULL REFERENCES community_actions(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, action_id)
);
CREATE INDEX volunteer_actions_action ON volunteer_actions(action_id);

-- o conversație per voluntar: el scrie din aplicație, Primăria (orice cont CITY_HALL / ADMIN) răspunde din panou;
-- `read_at` = când l-a văzut cealaltă parte
CREATE TABLE volunteer_messages (
    id                 BIGSERIAL     PRIMARY KEY,
    volunteer_user_id  BIGINT        NOT NULL REFERENCES volunteers(user_id) ON DELETE CASCADE,
    from_city          BOOLEAN       NOT NULL,
    author_id          BIGINT        NOT NULL REFERENCES users(id),
    text               VARCHAR(1000) NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    read_at            TIMESTAMPTZ
);
CREATE INDEX volunteer_messages_thread ON volunteer_messages(volunteer_user_id, created_at);

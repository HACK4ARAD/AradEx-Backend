-- T18 · Voluntari: conturi create doar de Primărie (cod de 6 cifre „prin SMS”), alocate pe acțiuni comunitare, cu un chat de
-- grup pe fiecare acțiune. Planul rezerva V10, dar între timp au intrat V11 (telefon, T21), V12 (transport, T22), V13 (admin, T14) și
-- V14 (SMS gateway): pe o bază nouă V10 ar rula înaintea lui V11, iar constrângerea „email sau telefon” din V11 ar pica la
-- conturile de voluntar (n-au niciunul). De aceea V15, primul număr liber.

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

-- chatul de grup al fiecărei acțiuni: toți voluntarii alocați acțiunii își scriu și își trimit poze (din aplicație).
-- Primăria nu e în conversație. O poză stă în bucket-ul de poze (`/api/v1/media/chat/…`), aici doar URL-ul.
CREATE TABLE action_chat_messages (
    id          BIGSERIAL     PRIMARY KEY,
    action_id   BIGINT        NOT NULL REFERENCES community_actions(id) ON DELETE CASCADE,
    author_id   BIGINT        NOT NULL REFERENCES users(id),
    text        VARCHAR(1000),
    image_url   VARCHAR(500),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT action_chat_text_or_image CHECK (text IS NOT NULL OR image_url IS NOT NULL)
);
CREATE INDEX action_chat_messages_thread ON action_chat_messages(action_id, id);

-- până unde a citit fiecare voluntar fiecare chat (punctul de pe tab-ul Mesaje și pe conversație)
CREATE TABLE action_chat_reads (
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action_id     BIGINT NOT NULL REFERENCES community_actions(id) ON DELETE CASCADE,
    last_read_id  BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, action_id)
);

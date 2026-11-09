-- V21: conturile personale (VISITOR, LOCAL) trec pe email + parolă; login-ul cu telefon + cod SMS dispare (gateway-ul de
-- SMS de pe Mac + iPhone și Twilio au fost scoase). Coloana users.phone rămâne (conturile demo și cele vechi o au).

DROP TABLE IF EXISTS sms_outbox;
DROP TABLE IF EXISTS otp_codes;

-- „Am uitat parola”: codul de 6 cifre trimis pe email, păstrat doar ca hash BCrypt; 15 minute, 5 încercări greșite.
CREATE TABLE password_reset_codes (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash    VARCHAR(100) NOT NULL,
    attempts     INT          NOT NULL DEFAULT 0,
    expires_at   TIMESTAMPTZ  NOT NULL,
    consumed_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX password_reset_codes_user_open ON password_reset_codes (user_id, created_at DESC) WHERE consumed_at IS NULL;

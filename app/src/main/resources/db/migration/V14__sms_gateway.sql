-- T24 · SMS-urile cu cod OTP trimise prin telefonul lui Zoltan (Mac + iPhone, aplicația Mesaje): coada pe care o citește
-- scriptul de pe Mac (AradEx-Ops/sms-gateway). Textul (cu codul) stă aici doar până la trimitere; după SENT / FAILED /
-- EXPIRED se șterge (body = NULL). Codul în sine e verificat din otp_codes (doar hash), cu providerul nou GATEWAY.
ALTER TABLE otp_codes DROP CONSTRAINT IF EXISTS otp_codes_provider_check;
ALTER TABLE otp_codes ADD CONSTRAINT otp_codes_provider_check CHECK (provider IN ('LOCAL', 'TWILIO', 'GATEWAY'));

CREATE TABLE sms_outbox (
    id           BIGSERIAL PRIMARY KEY,
    phone        VARCHAR(16)  NOT NULL,
    body         VARCHAR(200),
    status       VARCHAR(10)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'CLAIMED', 'SENT', 'FAILED', 'EXPIRED')),
    tries        INT          NOT NULL DEFAULT 0,
    send_before  TIMESTAMPTZ  NOT NULL,
    claimed_at   TIMESTAMPTZ,
    sent_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX sms_outbox_open ON sms_outbox (created_at) WHERE status IN ('PENDING', 'CLAIMED');

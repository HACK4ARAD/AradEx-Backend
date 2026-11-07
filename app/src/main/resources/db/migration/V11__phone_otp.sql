-- T21 · conturile personale (VISITOR, LOCAL) intră cu telefon + cod OTP; firmele, Primăria și Adminul rămân pe email + parolă.
-- Telefonul e în format E.164 (+40…). Un cont are email sau telefon (sau ambele, ca demo-urile din seed).
ALTER TABLE users ADD COLUMN phone VARCHAR(16) UNIQUE;
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE users ADD CONSTRAINT users_email_or_phone CHECK (email IS NOT NULL OR phone IS NOT NULL);

-- Codurile OTP: LOCAL = generat de noi (numerele de demo +40799000xxx), doar ca hash; TWILIO = trimis și verificat de
-- Twilio Verify (fără cod la noi). Rândurile servesc și la limite (o cerere la 60 s, 5 pe zi pe număr).
CREATE TABLE otp_codes (
    id           BIGSERIAL PRIMARY KEY,
    phone        VARCHAR(16)  NOT NULL,
    provider     VARCHAR(10)  NOT NULL CHECK (provider IN ('LOCAL', 'TWILIO')),
    code_hash    VARCHAR(100),
    attempts     INT          NOT NULL DEFAULT 0,
    expires_at   TIMESTAMPTZ  NOT NULL,
    consumed_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX otp_codes_phone_created ON otp_codes (phone, created_at DESC);

-- Numere de demo (fără SMS, codul apare în aplicație) pe conturile personale din seed, ca biletele, punctele și
-- clasamentul să rămână ale lor. Emailul rămâne în DB, dar conturile personale nu mai intră cu el.
UPDATE users SET phone = '+40799000001' WHERE id = 1;   -- visitor@demo.ro
UPDATE users SET phone = '+40799000002' WHERE id = 2;   -- local@demo.ro
UPDATE users SET phone = '+40799000003' WHERE id = 6;
UPDATE users SET phone = '+40799000004' WHERE id = 7;
UPDATE users SET phone = '+40799000005' WHERE id = 8;
UPDATE users SET phone = '+40799000006' WHERE id = 9;
UPDATE users SET phone = '+40799000007' WHERE id = 10;
UPDATE users SET phone = '+40799000008' WHERE id = 11;
UPDATE users SET phone = '+40799000009' WHERE id = 12;

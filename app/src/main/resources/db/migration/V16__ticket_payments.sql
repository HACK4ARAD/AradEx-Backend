-- V16: plata biletelor cu Stripe (modul de test). PaymentIntent-ul plătit e legat de un singur bilet, ca aceeași plată să nu
-- poată emite două bilete. NULL = bilet gratuit sau plată demonstrativă (fără chei Stripe). Vezi docs/DATA-MODEL.md.
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS payment_intent_id VARCHAR(100);
CREATE UNIQUE INDEX IF NOT EXISTS uq_tickets_payment_intent ON tickets(payment_intent_id) WHERE payment_intent_id IS NOT NULL;

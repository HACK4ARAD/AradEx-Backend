-- V16 (T25): plata biletelor cu cardul prin Stripe, în mod test (fără bani reali). Biletul plătit ține minte PaymentIntent-ul
-- Stripe; indexul unic face ca același PaymentIntent să dea un singur bilet (confirmarea din aplicație și webhook-ul pot veni
-- amândouă). Biletele fără plată (gratuite, plata demonstrativă) rămân cu NULL. Vezi docs/DATA-MODEL.md.
ALTER TABLE tickets ADD COLUMN IF NOT EXISTS payment_intent_id VARCHAR(64);
CREATE UNIQUE INDEX IF NOT EXISTS uq_tickets_payment_intent ON tickets(payment_intent_id) WHERE payment_intent_id IS NOT NULL;

-- V19_1: Over Dose Cafe (Arad) — 50% reducere la cafea cu codul QR din aplicație, ofertă promovată (prima în listă).
-- Contul firmei e de demo: overdose@demo.ro / demo1234 (ca celelalte conturi demo), cu el firma validează codurile la casă.
-- LOCAȚIE PROVIZORIE: centrul Aradului (Piața Avram Iancu) până primim adresa cafenelei; se corectează într-o migrare nouă
-- sau din profilul firmei. Idempotent (după email / proprietar / titlu), fără id-uri fixe.
INSERT INTO users (email, password_hash, display_name, role, locale, points_balance)
VALUES ('overdose@demo.ro', '$2a$10$FurXRhcQxrujUFWGKsDPaeK66hpMPWwvIMwIg2xwAFaldOgioPSXO', 'Over Dose Cafe', 'BUSINESS', 'ro', 0)
ON CONFLICT (email) DO NOTHING;

INSERT INTO businesses (owner_user_id, name, category, description, address, lat, lng, verified, views_count)
SELECT u.id, 'Over Dose Cafe', 'CAFE',
       $$Cafenea în Arad. Cu codul QR din AradExperience ai 50% reducere la cafea.$$,
       NULL, 46.1866, 21.3123, true, 0
FROM users u
WHERE u.email = 'overdose@demo.ro'
ON CONFLICT (owner_user_id) DO NOTHING;

INSERT INTO offers (business_id, title, description, discount_label, category, local_product, valid_from, valid_until, points_price,
                    image_url, status, featured)
SELECT b.id, '50% reducere la cafea',
       $$Revendică oferta, apoi arată codul QR din aplicație la Over Dose Cafe: primești 50% reducere la cafea. Un cod pe persoană, pentru o comandă.$$,
       '-50%', 'CAFE', false, DATE '2026-11-08', DATE '2026-11-30', NULL, '/img/oferta-cafea.jpg', 'PUBLISHED', true
FROM businesses b
JOIN users u ON u.id = b.owner_user_id AND u.email = 'overdose@demo.ro'
WHERE NOT EXISTS (SELECT 1 FROM offers o WHERE o.business_id = b.id AND o.title = '50% reducere la cafea');

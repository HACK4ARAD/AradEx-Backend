-- V7_1 (T10): oferte demo ale firmelor FICTIVE din V2_1 (vezi docs/ARAD-DATA.md) + câteva coduri folosite, ca panoul firmei
-- să aibă statistici și activitate. Brutăria Veche (2) și Grădina Bastion (6) nu sunt verificate → ofertele lor sunt DRAFT
-- până le verifică Primăria (T14). Idempotent.
INSERT INTO offers (id, business_id, title, description, discount_label, category, local_product, valid_from, valid_until, points_price, image_url, status, views_count, redemptions_count, used_count, created_at) VALUES
  (1, 1, '20% reducere la orice cafea', $$Arată codul la casă. Valabil de luni până sâmbătă, o dată pe zi, pentru o comandă. Sau plătește cu 30 de puncte. (FICTIV)$$, '-20%', 'CAFE', false, '2026-11-01', '2026-11-30', 30, '/img/oferta-cafea.jpg', 'PUBLISHED', 1240, 95, 87, '2026-11-01 08:00:00+02'),
  (2, 1, 'Prăjitura zilei -10%', $$Prăjitura zilei cu 10% reducere, la orice cafea. (FICTIV)$$, '-10%', 'CAFE', false, '2026-11-01', '2026-11-15', NULL, '/img/oferta-prajitura.jpg', 'PUBLISHED', 410, 26, 22, '2026-11-01 08:00:00+02'),
  (3, 1, 'Cappuccino cu puncte', $$Un cappuccino plătit integral cu punctele strânse la acțiunile comunitare. (FICTIV)$$, '40 p', 'CAFE', false, '2026-11-01', NULL, 40, '/img/loc-cappuccino.jpg', 'PUBLISHED', 960, 14, 12, '2026-11-01 08:00:00+02'),
  (4, 2, '2+1 la covrigii calzi', $$La trei covrigi, al treilea e din partea casei. Doar la cuptor, până la ora 11. (FICTIV)$$, '2+1', 'LOCAL', true, '2026-11-01', '2026-11-15', NULL, '/img/oferta-covrigi.jpg', 'DRAFT', 0, 0, 0, '2026-11-02 07:00:00+02'),
  (5, 3, 'Degustare de vinuri din Podgoria Miniș', $$Arată codul la cramă și primești un pahar în plus la degustarea de sâmbătă, 7 nov., ora 17:00. (FICTIV)$$, 'Eveniment', 'EVENT', true, '2026-11-01', '2026-11-07', NULL, '/img/oferta-degustare-vin.jpg', 'PUBLISHED', 980, 130, 120, '2026-11-01 09:00:00+02'),
  (6, 4, 'Miere de salcâm de la producători', $$Borcan de 500 g la 25 lei cu codul din aplicație, la standul din Piața Avram Iancu. (FICTIV)$$, 'Făcut în Arad', 'LOCAL', true, '2026-11-01', '2026-11-30', NULL, '/img/oferta-miere.jpg', 'PUBLISHED', 320, 20, 18, '2026-11-01 09:00:00+02'),
  (7, 5, 'Ciorbă de pește + desert · 35 lei', $$Meniul de prânz, de luni până vineri, 12:00–15:00, pe terasa de pe Faleză. (FICTIV)$$, 'Meniu', 'RESTAURANT', false, '2026-11-01', '2026-11-20', NULL, '/img/loc-ciorba-de-peste.jpg', 'PUBLISHED', 530, 40, 34, '2026-11-01 10:00:00+02'),
  (8, 6, '-15% la grătar, duminica', $$Se publică după verificarea firmei de către Primărie. (FICTIV)$$, '-15%', 'RESTAURANT', false, '2026-11-08', '2026-11-29', NULL, '/img/loc-gradina-restaurant.jpg', 'DRAFT', 0, 0, 0, '2026-11-07 09:12:00+02')
ON CONFLICT (id) DO NOTHING;

INSERT INTO offer_redemptions (id, offer_id, user_id, code, points_spent, status, issued_at, used_at, used_by_user_id) VALUES
  (1, 1, 10, 'AX-O1-B9Q2LW', 0, 'USED', '2026-11-06 08:55:00+02', '2026-11-06 09:12:00+02', 3),
  (2, 2, 11, 'AX-O2-M2N8RT', 0, 'USED', '2026-11-06 09:40:00+02', '2026-11-06 09:58:00+02', 3),
  (3, 3, 12, 'AX-O3-Q5W7ZX', 0, 'USED', '2026-11-05 18:10:00+02', '2026-11-05 18:30:00+02', 3),
  (4, 2, 10, 'AX-O2-H4K9PD', 0, 'ISSUED', '2026-11-07 08:20:00+02', NULL, NULL)
ON CONFLICT (id) DO NOTHING;

SELECT setval('offers_id_seq', GREATEST((SELECT MAX(id) FROM offers), 1));
SELECT setval('offer_redemptions_id_seq', GREATEST((SELECT MAX(id) FROM offer_redemptions), 1));

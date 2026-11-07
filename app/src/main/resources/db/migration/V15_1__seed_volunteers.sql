-- T18 · seed V15_1 (FICTIV, ca în design/final): 2 coordonatori ai Primăriei și 2 voluntari cu codurile din design
-- (482913 Andrei Popa, 731508 Maria Ionescu), alocați la acțiunile 1–3 din V8_1, plus conversațiile din design.
-- Idempotent: ON CONFLICT DO NOTHING + setval.

INSERT INTO users (id, email, password_hash, display_name, role, locale, points_balance) VALUES
  (201, 'ioana.marin@demo.ro', '$2a$10$FurXRhcQxrujUFWGKsDPaeK66hpMPWwvIMwIg2xwAFaldOgioPSXO', 'Ioana Marin', 'CITY_HALL', 'ro', 0),
  (202, 'radu.toma@demo.ro',   '$2a$10$FurXRhcQxrujUFWGKsDPaeK66hpMPWwvIMwIg2xwAFaldOgioPSXO', 'Radu Toma',   'CITY_HALL', 'ro', 0),
  (203, NULL, NULL, 'Andrei Popa',   'VOLUNTEER', 'ro', 0),
  (204, NULL, NULL, 'Maria Ionescu', 'VOLUNTEER', 'ro', 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO volunteers (user_id, phone, email, login_code, one_time, status, coordinator_id, created_by, created_at) VALUES
  (203, '+40740000001', 'andrei.popa@exemplu.ro',   '482913', false, 'ACTIVE', 201, 4, '2026-11-05 10:00:00+02'),
  (204, '+40740000002', 'maria.ionescu@exemplu.ro', '731508', true,  'ACTIVE', 201, 4, '2026-11-05 10:05:00+02')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO volunteer_actions (user_id, action_id) VALUES (203, 1), (203, 2), (204, 3)
ON CONFLICT DO NOTHING;

INSERT INTO volunteer_messages (id, volunteer_user_id, from_city, author_id, text, created_at, read_at) VALUES
  (1, 203, true,  201, 'Bună, Andrei! Ești alocat la acțiunile de mai jos. La intrare scanezi codul QR al fiecărui participant. Scrie-mi aici dacă ai nevoie de ceva.', '2026-11-06 17:02:00+02', '2026-11-06 17:30:00+02'),
  (2, 203, false, 203, 'Mulțumesc! Sâmbătă ajung la 09:15 la foișor.', '2026-11-06 17:40:00+02', '2026-11-06 17:43:00+02'),
  (3, 203, true,  201, 'Perfect. Mănușile și uneltele sunt la mașina Direcției Spații Verzi.', '2026-11-06 17:44:00+02', '2026-11-06 18:10:00+02'),
  (4, 204, true,  201, 'Bună, Maria! Ești alocată la donarea de sânge de marți. Scanezi codurile la recepție, de la 07:30.', '2026-11-06 17:05:00+02', NULL)
ON CONFLICT (id) DO NOTHING;

-- Andrei a scanat deja doi participanți la plantarea de azi (în V8_1 îi confirmase Primăria)
UPDATE community_registrations SET confirmed_by = 203 WHERE id IN (84, 85) AND confirmed_by = 4;

SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));
SELECT setval('volunteer_messages_id_seq', (SELECT MAX(id) FROM volunteer_messages));

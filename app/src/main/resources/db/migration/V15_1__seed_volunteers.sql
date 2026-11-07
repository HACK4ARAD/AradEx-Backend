-- T18 · seed V15_1 (FICTIV, ca în design/final): 2 coordonatori ai Primăriei și 2 voluntari cu codurile din design
-- (482913 Andrei Popa, 731508 Maria Ionescu), alocați la acțiunile 1–3 din V8_1. Amândoi sunt la plantare (acțiunea 1),
-- unde au deja un chat de grup cu câteva mesaje și o poză.
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

INSERT INTO volunteer_actions (user_id, action_id) VALUES (203, 1), (203, 2), (204, 1), (204, 3)
ON CONFLICT DO NOTHING;

INSERT INTO action_chat_messages (id, action_id, author_id, text, image_url, created_at) VALUES
  (1, 1, 203, 'Bună, Maria! Ajung la 09:15 la foișor, scanăm amândoi la intrare?', NULL, '2026-11-07 08:40:00+02'),
  (2, 1, 204, 'Da! Eu iau sacii și mănușile de la mașina Direcției Spații Verzi.', NULL, '2026-11-07 08:52:00+02'),
  (3, 1, 204, 'Așa arată locul acum', '/img/actiune-plantare-copaci.jpg', '2026-11-07 09:20:00+02'),
  (4, 1, 203, 'Super, mă văd cu tine lângă foișor.', NULL, '2026-11-07 09:24:00+02')
ON CONFLICT (id) DO NOTHING;

INSERT INTO action_chat_reads (user_id, action_id, last_read_id) VALUES (203, 1, 2), (204, 1, 4)
ON CONFLICT DO NOTHING;

-- Andrei a scanat deja doi participanți la plantarea de azi (în V8_1 îi confirmase Primăria)
UPDATE community_registrations SET confirmed_by = 203 WHERE id IN (84, 85) AND confirmed_by = 4;

SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));
SELECT setval('action_chat_messages_id_seq', (SELECT MAX(id) FROM action_chat_messages));

-- V23 (11 nov): activitate DEMO, ca panourile (Admin, Primărie, firme) să arate ca într-un oraș care folosește aplicația, cât
-- timp aplicația e „doar demo” (fără conturi reale). Totul e INVENTAT: 60 de utilizatori cu nume fictive (fără parolă, deci
-- nu se poate intra pe ei; adrese @demo.ro), bilete, coduri de ofertă, recenzii, evenimente salvate, puncte și 6 voluntari cu
-- telefoane fictive (+40 700 000 1xx). Rândurile se leagă de evenimentele / ofertele / acțiunile existente după tip și stare,
-- nu după id, ca migrarea să meargă la fel local și în producție. Datele sunt relative la momentul rulării (ultimele ~60 zile).
-- Rândurile au id-uri / coduri fixe (ON CONFLICT DO NOTHING); contoarele denormalizate primesc doar ce adaugă migrarea.

-- ---------------------------------------------------------------- utilizatori fictivi (5001–5060)
INSERT INTO users (id, email, password_hash, display_name, role, locale, points_balance, created_at, terms_accepted_at, terms_version)
SELECT 5000 + n,
       lower(f.ascii[1 + n % 20] || '.' || l.ascii[1 + n % 23] || '@demo.ro'),
       NULL,
       f.ro[1 + n % 20] || ' ' || l.ro[1 + n % 23],
       CASE WHEN n % 4 = 0 THEN 'VISITOR' ELSE 'LOCAL' END,
       CASE WHEN n % 9 = 0 THEN 'hu' WHEN n % 11 = 0 THEN 'en' ELSE 'ro' END,
       0,
       now() - ((n * 7) % 58 + 2) * interval '1 day',
       now() - ((n * 7) % 58 + 2) * interval '1 day',
       '2026-11-08'
FROM generate_series(1, 60) AS n,
     (SELECT ARRAY['Ana','Mihai','Elena','Andrei','Ioana','Alexandru','Maria','Bogdan','Cristina','Vlad',
                   'Raluca','Ștefan','Diana','Gabriel','Larisa','Tudor','Bianca','Răzvan','Alina','Sorin'] AS ro,
             ARRAY['ana','mihai','elena','andrei','ioana','alexandru','maria','bogdan','cristina','vlad',
                   'raluca','stefan','diana','gabriel','larisa','tudor','bianca','razvan','alina','sorin'] AS ascii) f,
     (SELECT ARRAY['Popa','Mureșan','Stan','Lazăr','Dobre','Bălan','Oprea','Crișan','Moldovan','Ilie','Neagu','Tănase',
                   'Barbu','Iancu','Nistor','Sârbu','Cozma','Dragomir','Florea','Ardelean','Vasile','Marinescu','Pop'] AS ro,
             ARRAY['popa','muresan','stan','lazar','dobre','balan','oprea','crisan','moldovan','ilie','neagu','tanase',
                   'barbu','iancu','nistor','sarbu','cozma','dragomir','florea','ardelean','vasile','marinescu','pop'] AS ascii) l
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------- bilete la evenimentele cu bilet în aplicație
-- câte un bilet de 1–3 locuri de la o treime din utilizatori, cât timp mai sunt locuri (max. 70% din capacitate)
INSERT INTO tickets (event_id, user_id, qty_adult, qty_child, total, code, status, email, used_at, created_at)
SELECT t.event_id, t.user_id, t.qa, t.qc, t.qa * t.pa + t.qc * t.pc, t.code,
       CASE WHEN t.start_at < now() THEN 'USED' ELSE 'CONFIRMED' END, t.email,
       CASE WHEN t.start_at < now() THEN t.start_at - interval '12 minutes' END,
       LEAST(t.start_at, now()) - ((t.user_id % 20) + 1) * interval '1 day'
FROM (
    SELECT e.id AS event_id, u.id AS user_id, u.email, e.start_at,
           1 + (u.id % 2) AS qa, CASE WHEN u.id % 5 = 0 THEN 1 ELSE 0 END AS qc,
           COALESCE(e.price_adult, e.price_from, 0) AS pa, COALESCE(e.price_child, 0) AS pc,
           'AX-T' || e.id || '-D' || (u.id - 5000) AS code,
           e.capacity AS cap,
           sum(1 + (u.id % 2) + CASE WHEN u.id % 5 = 0 THEN 1 ELSE 0 END) OVER (PARTITION BY e.id ORDER BY u.id) AS running
    FROM events e
    JOIN users u ON u.id BETWEEN 5001 AND 5060 AND (u.id + e.id) % 3 = 0
    WHERE e.ticket_type = 'IN_APP' AND e.status = 'PUBLISHED'
) t
WHERE t.cap IS NULL OR t.running <= floor(t.cap * 0.7)
ON CONFLICT (code) DO NOTHING;

-- ---------------------------------------------------------------- coduri de ofertă (o cincime din utilizatori, la fiecare ofertă publicată)
INSERT INTO offer_redemptions (offer_id, user_id, code, points_spent, status, issued_at, used_at, used_by_user_id)
SELECT o.id, u.id, 'AX-O' || o.id || '-D' || (u.id - 5000), COALESCE(o.points_price, 0),
       CASE WHEN u.id % 4 = 0 THEN 'ISSUED' ELSE 'USED' END,
       now() - ((u.id % 25) + 1) * interval '1 day',
       CASE WHEN u.id % 4 = 0 THEN NULL ELSE now() - ((u.id % 25) + 1) * interval '1 day' + interval '25 minutes' END,
       CASE WHEN u.id % 4 = 0 THEN NULL ELSE b.owner_user_id END
FROM offers o
JOIN businesses b ON b.id = o.business_id
JOIN users u ON u.id BETWEEN 5001 AND 5060 AND (u.id + o.id) % 5 = 0
WHERE o.status = 'PUBLISHED'
ON CONFLICT (code) DO NOTHING;

-- punctele cheltuite pe ofertele cu preț în puncte
INSERT INTO points_transactions (user_id, amount, type, ref_type, ref_id, note, created_at)
SELECT r.user_id, -r.points_spent, 'SPEND_OFFER', 'OFFER', r.offer_id, 'Ofertă revendicată (demo)', r.issued_at
FROM offer_redemptions r
WHERE r.code LIKE 'AX-O%-D%' AND r.points_spent > 0
  AND NOT EXISTS (SELECT 1 FROM points_transactions p WHERE p.user_id = r.user_id AND p.ref_type = 'OFFER' AND p.ref_id = r.offer_id);

-- ---------------------------------------------------------------- 4 acțiuni comunitare care urmează (DEMO, inventate)
INSERT INTO community_actions (title, description, category, organizer, start_at, end_at, location_name, address, lat, lng,
                               points_reward, capacity, registered_count, confirmed_count, image_url, image_credit, status, created_by, created_at)
SELECT v.title, v.descr, v.cat, v.org,
       date_trunc('day', now()) + v.days * interval '1 day' + interval '7 hours',
       date_trunc('day', now()) + v.days * interval '1 day' + interval '10 hours',
       v.loc, v.addr, v.lat, v.lng, v.pts, v.cap, 0, 0, v.img, v.credit, 'PUBLISHED', 4, now() - interval '5 days'
FROM (VALUES
  ('Plantăm puieți în Pădurea Ceala', $$ACȚIUNE DEMO. Plantăm 300 de puieți de stejar împreună cu Ocolul Silvic; uneltele și mănușile le primești la fața locului.$$,
   'ENVIRONMENT', 'Direcția Spații Verzi', 9, 'Pădurea Ceala, parcarea de la intrare', 'Lunca Mureșului, Arad', 46.19, 21.265, 60, 50,
   '/img/actiune-plantare-copaci.jpg', 'Foto: U.S. Air Force, Christopher Tam · Domeniu public · Wikimedia Commons'),
  ('Colectăm haine de iarnă pentru adăpost', $$ACȚIUNE DEMO. Sortăm și ambalăm hainele donate de arădeni pentru adăpostul de noapte; vino cu o pereche de mănuși.$$,
   'SOCIAL', 'Direcția de Asistență Socială', 16, 'Sala Clio, Primăria Arad', 'Bd. Revoluției 75, Arad', 46.1866, 21.3123, 40, 30,
   '/img/actiune-curatenie.jpg', 'Foto: David Lally · CC BY-SA 2.0 · Wikimedia Commons'),
  ('Curățenie de iarnă pe Faleza Mureșului', $$ACȚIUNE DEMO. Strângem gunoaiele de pe faleză, de la Podul Traian până la Ștrandul Neptun; sacii și mănușile le asigurăm noi.$$,
   'ENVIRONMENT', 'Direcția Spații Verzi', 23, 'Faleza Mureșului, foișorul de lângă Podul Traian', 'Faleza Mureșului, Arad', 46.1745, 21.3215, 50, 60,
   '/img/actiune-curatenie.jpg', 'Foto: David Lally · CC BY-SA 2.0 · Wikimedia Commons'),
  ('Donăm sânge de sărbători', $$ACȚIUNE DEMO. O dimineață de donare la Centrul de Transfuzie: vino odihnit, după un mic dejun ușor, cu buletinul.$$,
   'HEALTH', 'Centrul de Transfuzie Sanguină', 30, 'Centrul de Transfuzie Sanguină Arad', 'Str. Andrei Șaguna, Arad', 46.1801, 21.309, 80, 30,
   '/img/actiune-donare-sange.jpg', 'Foto: Shixart1985 · CC BY 2.0 · Wikimedia Commons')
) AS v(title, descr, cat, org, days, loc, addr, lat, lng, pts, cap, img, credit)
WHERE NOT EXISTS (SELECT 1 FROM community_actions a WHERE a.title = v.title);

-- ---------------------------------------------------------------- acțiuni comunitare: înscrieri confirmate + puncte (localnicii)
INSERT INTO community_registrations (action_id, user_id, code, status, registered_at, confirmed_at, confirmed_by)
SELECT a.id, u.id, 'AX-C' || a.id || '-D' || (u.id - 5000), 'CONFIRMED',
       a.start_at - interval '3 days', a.start_at + interval '40 minutes', 4
FROM community_actions a
JOIN users u ON u.id BETWEEN 5001 AND 5060 AND u.role = 'LOCAL' AND (u.id + a.id) % 4 = 0
WHERE a.start_at < now() AND a.status = 'PUBLISHED'
ON CONFLICT DO NOTHING;

-- și înscrieri (încă neconfirmate) la acțiunile care urmează
INSERT INTO community_registrations (action_id, user_id, code, status, registered_at)
SELECT a.id, u.id, 'AX-C' || a.id || '-D' || (u.id - 5000), 'REGISTERED', now() - ((u.id % 6) + 1) * interval '1 day'
FROM community_actions a
JOIN users u ON u.id BETWEEN 5001 AND 5060 AND u.role = 'LOCAL' AND (u.id + a.id) % 3 = 0
WHERE a.start_at > now() AND a.status = 'PUBLISHED'
ON CONFLICT DO NOTHING;

INSERT INTO points_transactions (user_id, amount, type, ref_type, ref_id, note, created_at)
SELECT r.user_id, a.points_reward, 'EARN_ACTION', 'ACTION', a.id, a.title, r.confirmed_at
FROM community_registrations r
JOIN community_actions a ON a.id = r.action_id
WHERE r.code LIKE 'AX-C%-D%' AND r.status = 'CONFIRMED' AND a.points_reward > 0
  AND NOT EXISTS (SELECT 1 FROM points_transactions p WHERE p.user_id = r.user_id AND p.ref_type = 'ACTION' AND p.ref_id = a.id);

-- ---------------------------------------------------------------- recenzii la evenimentele care au trecut
INSERT INTO reviews (event_id, user_id, stars, text, status, created_at, updated_at)
SELECT e.id, u.id,
       CASE WHEN u.id % 10 = 0 THEN 3 WHEN u.id % 10 IN (1, 2) THEN 4 ELSE 5 END,
       (ARRAY['Foarte frumos organizat, revin sigur!', 'Atmosferă super, recomand cu drag.', 'Un pic aglomerat la intrare, dar a meritat.',
              'Perfect pentru o seară cu prietenii.', 'Am venit cu copiii și ne-a plăcut tuturor.', 'Sunet excelent și oameni faini.',
              'Bine că am găsit evenimentul în aplicație!', 'Locația e superbă, programul a fost respectat.', 'Mi-ar plăcea să fie mai des.',
              'Organizare impecabilă, mulțumim!', 'Bilete ușor de cumpărat, intrarea cu QR a mers rapid.', 'O seară reușită în centrul Aradului.'])[1 + u.id % 12],
       'PUBLISHED', e.end_at + ((u.id % 5) + 1) * interval '1 hour', e.end_at + ((u.id % 5) + 1) * interval '1 hour'
FROM events e
JOIN users u ON u.id BETWEEN 5001 AND 5060 AND (u.id + e.id) % 7 = 0
WHERE e.status = 'PUBLISHED' AND e.end_at < now()
ON CONFLICT (event_id, user_id) DO NOTHING;

-- ---------------------------------------------------------------- evenimente salvate (cele care urmează)
INSERT INTO saved_events (user_id, event_id, created_at)
SELECT u.id, e.id, now() - ((u.id % 10) + 1) * interval '1 day'
FROM events e
JOIN users u ON u.id BETWEEN 5001 AND 5060 AND (u.id * 3 + e.id) % 8 = 0
WHERE e.status = 'PUBLISHED' AND e.start_at > now()
  AND NOT EXISTS (SELECT 1 FROM saved_events s WHERE s.user_id = u.id AND s.event_id = e.id);

-- ---------------------------------------------------------------- voluntari fictivi (5101–5106), coordonați de Ioana Marin (201)
INSERT INTO users (id, email, password_hash, display_name, role, locale, points_balance, created_at)
VALUES (5101, NULL, NULL, 'Paul Rusu', 'VOLUNTEER', 'ro', 0, now() - interval '20 days'),
       (5102, NULL, NULL, 'Carmen Avram', 'VOLUNTEER', 'ro', 0, now() - interval '18 days'),
       (5103, NULL, NULL, 'Denis Lupu', 'VOLUNTEER', 'ro', 0, now() - interval '15 days'),
       (5104, NULL, NULL, 'Oana Herman', 'VOLUNTEER', 'ro', 0, now() - interval '12 days'),
       (5105, NULL, NULL, 'Iulian Matei', 'VOLUNTEER', 'ro', 0, now() - interval '9 days'),
       (5106, NULL, NULL, 'Teodora Gal', 'VOLUNTEER', 'ro', 0, now() - interval '6 days')
ON CONFLICT DO NOTHING;

INSERT INTO volunteers (user_id, phone, email, login_code, one_time, status, coordinator_id, created_by, created_at)
SELECT v.id, '+40700000' || (100 + v.id - 5100), NULL, '61010' || (v.id - 5100), (v.id % 3 = 0),
       CASE WHEN v.id = 5106 THEN 'SUSPENDED' ELSE 'ACTIVE' END,
       201, 4, u.created_at
FROM (SELECT generate_series(5101, 5106) AS id) v
JOIN users u ON u.id = v.id
WHERE EXISTS (SELECT 1 FROM users c WHERE c.id = 201) AND EXISTS (SELECT 1 FROM users c WHERE c.id = 4)
ON CONFLICT DO NOTHING;

INSERT INTO volunteer_actions (user_id, action_id)
SELECT v.user_id, a.id
FROM volunteers v
JOIN community_actions a ON a.status = 'PUBLISHED' AND (v.user_id + a.id) % 3 = 0
WHERE v.user_id BETWEEN 5101 AND 5106
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------- contoarele, din tabele
UPDATE users u SET points_balance = GREATEST(0, (SELECT COALESCE(sum(p.amount), 0) FROM points_transactions p WHERE p.user_id = u.id))
WHERE u.id BETWEEN 5001 AND 5060;

-- contoarele vechi pot veni din seed-uri fără toate rândurile: le adunăm doar ce adaugă migrarea asta
UPDATE offers o SET redemptions_count = redemptions_count + (SELECT count(*) FROM offer_redemptions r WHERE r.offer_id = o.id AND r.code LIKE 'AX-O%-D%'),
                    used_count        = used_count + (SELECT count(*) FROM offer_redemptions r WHERE r.offer_id = o.id AND r.code LIKE 'AX-O%-D%' AND r.status = 'USED');

UPDATE events e SET saves_count = saves_count + (SELECT count(*) FROM saved_events s WHERE s.event_id = e.id AND s.user_id BETWEEN 5001 AND 5060);

UPDATE community_actions a SET registered_count = registered_count + (SELECT count(*) FROM community_registrations r WHERE r.action_id = a.id AND r.code LIKE 'AX-C%-D%'),
                               confirmed_count  = confirmed_count + (SELECT count(*) FROM community_registrations r WHERE r.action_id = a.id AND r.code LIKE 'AX-C%-D%' AND r.status = 'CONFIRMED');

SELECT setval('users_id_seq', GREATEST((SELECT max(id) FROM users), 1));

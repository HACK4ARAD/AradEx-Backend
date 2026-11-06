-- V2_1 (T03): conturi demo + firme FICTIVE pentru demo. Parola pentru toate: demo1234 (BCrypt, cost 10).
-- Firmele de aici sunt inventate (vezi docs/ARAD-DATA.md) — nu sunt firme reale din Arad.

INSERT INTO users (id, email, password_hash, display_name, role, locale, points_balance) VALUES
  (1, 'visitor@demo.ro',  '$2a$10$FurXRhcQxrujUFWGKsDPaeK66hpMPWwvIMwIg2xwAFaldOgioPSXO', 'Ana P.',               'VISITOR',   'ro', 0),
  (2, 'local@demo.ro',    '$2a$10$FurXRhcQxrujUFWGKsDPaeK66hpMPWwvIMwIg2xwAFaldOgioPSXO', 'Ana Popescu',          'LOCAL',     'ro', 270),
  (3, 'firma@demo.ro',    '$2a$10$FurXRhcQxrujUFWGKsDPaeK66hpMPWwvIMwIg2xwAFaldOgioPSXO', 'Cafeneaua din Pasaj',  'BUSINESS',  'ro', 0),
  (4, 'primaria@demo.ro', '$2a$10$FurXRhcQxrujUFWGKsDPaeK66hpMPWwvIMwIg2xwAFaldOgioPSXO', 'Primăria Arad',        'CITY_HALL', 'ro', 0),
  (5, 'admin@demo.ro',    '$2a$10$FurXRhcQxrujUFWGKsDPaeK66hpMPWwvIMwIg2xwAFaldOgioPSXO', 'Admin',                'ADMIN',     'ro', 0),
  -- localnici fictivi pentru clasament și liste de înscriși
  (6,  'mihai.r@exemplu.ro',  '$2a$10$FurXRhcQxrujUFWGKsDPaeK66hpMPWwvIMwIg2xwAFaldOgioPSXO', 'Mihai R.',  'LOCAL', 'ro', 980),
  (7,  'ioana.s@exemplu.ro',  '$2a$10$FurXRhcQxrujUFWGKsDPaeK66hpMPWwvIMwIg2xwAFaldOgioPSXO', 'Ioana S.',  'LOCAL', 'ro', 910),
  (8,  'andrei.p@exemplu.ro', '$2a$10$FurXRhcQxrujUFWGKsDPaeK66hpMPWwvIMwIg2xwAFaldOgioPSXO', 'Andrei P.', 'LOCAL', 'ro', 860),
  (9,  'elena.v@exemplu.ro',  '$2a$10$FurXRhcQxrujUFWGKsDPaeK66hpMPWwvIMwIg2xwAFaldOgioPSXO', 'Elena V.',  'LOCAL', 'ro', 790),
  (10, 'kovacs.e@exemplu.ro', '$2a$10$FurXRhcQxrujUFWGKsDPaeK66hpMPWwvIMwIg2xwAFaldOgioPSXO', 'Kovács E.', 'LOCAL', 'ro', 740),
  (11, 'radu.t@exemplu.ro',   '$2a$10$FurXRhcQxrujUFWGKsDPaeK66hpMPWwvIMwIg2xwAFaldOgioPSXO', 'Radu T.',   'LOCAL', 'ro', 690),
  (12, 'diana.m@exemplu.ro',  '$2a$10$FurXRhcQxrujUFWGKsDPaeK66hpMPWwvIMwIg2xwAFaldOgioPSXO', 'Diana M.',  'LOCAL', 'ro', 610),
  -- proprietari pentru celelalte firme fictive
  (13, 'brutarie@exemplu.ro', '$2a$10$FurXRhcQxrujUFWGKsDPaeK66hpMPWwvIMwIg2xwAFaldOgioPSXO', 'Brutăria Veche',              'BUSINESS', 'ro', 0),
  (14, 'crama@exemplu.ro',    '$2a$10$FurXRhcQxrujUFWGKsDPaeK66hpMPWwvIMwIg2xwAFaldOgioPSXO', 'Crama Dealul Miniș',          'BUSINESS', 'ro', 0),
  (15, 'producatori@exemplu.ro','$2a$10$FurXRhcQxrujUFWGKsDPaeK66hpMPWwvIMwIg2xwAFaldOgioPSXO','Producători din județul Arad','BUSINESS', 'ro', 0),
  (16, 'bistro@exemplu.ro',   '$2a$10$FurXRhcQxrujUFWGKsDPaeK66hpMPWwvIMwIg2xwAFaldOgioPSXO', 'Bistro La Mureș',             'BUSINESS', 'ro', 0),
  (17, 'bastion@exemplu.ro',  '$2a$10$FurXRhcQxrujUFWGKsDPaeK66hpMPWwvIMwIg2xwAFaldOgioPSXO', 'Grădina Bastion',             'BUSINESS', 'ro', 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO businesses (id, owner_user_id, name, category, description, address, lat, lng, phone, website, image_url, verified, views_count) VALUES
  (1, 3,  'Cafeneaua din Pasaj', 'CAFE', 'Cafenea de specialitate lângă Teatrul Clasic. Prăjim local, avem lapte vegetal și o terasă mică în curte. Vinerea, de la 19:30, jazz live. (FICTIV)', 'Str. Vasile Alecsandri 1, Arad', 46.1730, 21.3148, NULL, NULL, '/img/loc-cappuccino.jpg', true, 1240),
  (2, 13, 'Brutăria Veche', 'PRODUCER', 'Pâine cu maia și covrigi calzi, coapte în cuptor cu vatră. (FICTIV)', 'Str. Mețianu 3, Arad', 46.1712, 21.3140, NULL, NULL, '/img/oferta-covrigi.jpg', false, 410),
  (3, 14, 'Crama Dealul Miniș', 'WINERY', 'Cramă de familie în Podgoria Miniș–Măderat: Cadarcă, Mustoasă de Măderat, Fetească regală. Degustări cu rezervare. (FICTIV)', 'Miniș, comuna Ghioroc', 46.1340, 21.6180, NULL, NULL, '/img/oferta-degustare-vin.jpg', true, 880),
  (4, 15, 'Producători din județul Arad', 'PRODUCER', 'Asociație de mici producători: miere, brânzeturi, dulcețuri, legume de sezon. (FICTIV)', 'Piața Avram Iancu, Arad', 46.1722, 21.3158, NULL, NULL, '/img/oferta-miere.jpg', true, 560),
  (5, 16, 'Bistro La Mureș', 'RESTAURANT', 'Bistro pe Faleză, ciorbă de pește și preparate de sezon. (FICTIV)', 'Faleza Mureșului, Arad', 46.1748, 21.3225, NULL, NULL, '/img/loc-ciorba-de-peste.jpg', true, 730),
  (6, 17, 'Grădina Bastion', 'RESTAURANT', 'Grădină de vară cu grătar, în Subcetate, cu vedere spre zidurile Cetății. (FICTIV)', 'Piața 13 Martiri, Arad', 46.1700, 21.3300, NULL, NULL, '/img/loc-gradina-restaurant.jpg', false, 300)
ON CONFLICT (id) DO NOTHING;

SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));
SELECT setval('businesses_id_seq', (SELECT MAX(id) FROM businesses));

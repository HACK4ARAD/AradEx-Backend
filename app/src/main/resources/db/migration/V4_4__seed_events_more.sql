-- V4_4 (T06, date reale): evenimentele reale din AradEx-Plan/docs/ARAD-DATA.md care lipseau din V4_1.
-- Filarmonica (10 și 17 dec), concertele de colinde din weekend de la Târgul de Crăciun (18:00–20:00) și expozițiile
-- permanente ale Complexului Muzeal (singurul eveniment EXHIBITION; marți–duminică 09:00–17:00, 13,5 lei/secție).
-- Nu intră cele fără oră sau titlu confirmat în ARAD-DATA (UTA – U Cluj, spectacolul din 29 nov, concertul din 1 dec).
-- Coordonatele sunt cele corectate (V3_2 / V5_2). Idempotent (titlu + început), id-uri din secvență.

INSERT INTO events (title, description, category, organizer_type, organizer_name, place_id, location_name, address, lat, lng,
                    start_at, end_at, image_url, ticket_type, price_from, price_adult, external_url, status, is_demo, created_by)
SELECT v.title, v.description, v.category, v.organizer_type, v.organizer_name, v.place_id, v.location_name, v.address, v.lat, v.lng,
       v.start_at, v.end_at, v.image_url, v.ticket_type, v.price_from, v.price_adult, v.external_url, 'PUBLISHED', false,
       (SELECT id FROM users WHERE email = v.creator)
FROM (VALUES
  ('Complexul Muzeal Arad: „Tezaur 1848–1849” și sala imersivă',
   $$Expozițiile permanente din Palatul Cultural: „Tezaur 1848–1849”, cu cele mai valoroase obiecte ale colecției, sala imersivă despre fortificația din epoca bronzului de la Sântana–Cetatea Veche, Muzeul de Științe ale Naturii și Pinacoteca. Marți–duminică 09:00–17:00 (ultima intrare 16:30), închis luni și pe 30 noiembrie. Bilet 13,5 lei pe secție (pensionari 7,5, elevi 3), Pinacoteca 16,5 lei, toate expozițiile 47 lei. Intrarea dinspre Bd. Dragalina / Parcul Copiilor.$$,
   'EXHIBITION', 'EXTERNAL', 'Complexul Muzeal Arad', 2, 'Palatul Cultural – Complexul Muzeal', 'Piața George Enescu 1, Arad (intrarea dinspre Bd. Dragalina)',
   46.1742, 21.3207, TIMESTAMPTZ '2026-11-03 09:00:00+02', TIMESTAMPTZ '2026-12-20 17:00:00+02', '/img/loc-pc.jpg',
   'EXTERNAL', 13.50, 13.50, 'https://www.museumarad.ro/program-de-vizitare', 'admin@demo.ro'),
  ('Crăciunul la Filarmonică',
   $$Concert de Crăciun cu Orchestra Filarmonicii Arad, în Sala Palatului Cultural. Bilete la casieria Filarmonicii (în zi de concert 09:00–13:00 și 17:00–18:45) și pe filarmonicaarad.ro.$$,
   'CONCERT', 'EXTERNAL', 'Filarmonica Arad', 2, 'Sala Palatului Cultural', 'Piața George Enescu 1, Arad',
   46.1742, 21.3207, TIMESTAMPTZ '2026-12-10 19:00:00+02', TIMESTAMPTZ '2026-12-10 21:00:00+02', '/img/loc-pc.jpg',
   'EXTERNAL', NULL, NULL, 'https://www.filarmonicaarad.ro/', 'admin@demo.ro'),
  ('„Colindăm, colindăm iarna” – Q Band',
   $$Concert de colinde și cântece de iarnă cu Q Band, în stagiunea Filarmonicii Arad. Bilete la casieria Filarmonicii (în zi de concert 09:00–13:00 și 17:00–18:45) și pe filarmonicaarad.ro.$$,
   'CONCERT', 'EXTERNAL', 'Filarmonica Arad', 2, 'Sala Palatului Cultural', 'Piața George Enescu 1, Arad',
   46.1742, 21.3207, TIMESTAMPTZ '2026-12-17 19:00:00+02', TIMESTAMPTZ '2026-12-17 21:00:00+02', '/img/loc-pc.jpg',
   'EXTERNAL', NULL, NULL, 'https://www.filarmonicaarad.ro/', 'admin@demo.ro'),
  ('Târgul de Crăciun – concert de colinde',
   $$Concert de colinde pe scena din Piața Catedralei, în weekendul Târgului de Crăciun (în fiecare weekend, 18:00–20:00). Intrare liberă; căsuțele și food-truck-urile sunt deschise zilnic 14:00–22:00.$$,
   'CONCERT', 'CITY', 'Primăria Arad & Centrul Municipal de Cultură', 6, 'Piața Catedralei', 'Piața Catedralei, Arad',
   46.1832, 21.3240, TIMESTAMPTZ '2026-11-22 18:00:00+02', TIMESTAMPTZ '2026-11-22 20:00:00+02', '/img/loc-cs.jpg',
   'FREE', 0, NULL, 'https://www.facebook.com/targuldecraciunarad/', 'primaria@demo.ro'),
  ('Târgul de Crăciun – concert de colinde',
   $$Concert de colinde pe scena din Piața Catedralei, în weekendul Târgului de Crăciun (în fiecare weekend, 18:00–20:00). Intrare liberă; căsuțele și food-truck-urile sunt deschise zilnic 14:00–22:00.$$,
   'CONCERT', 'CITY', 'Primăria Arad & Centrul Municipal de Cultură', 6, 'Piața Catedralei', 'Piața Catedralei, Arad',
   46.1832, 21.3240, TIMESTAMPTZ '2026-11-28 18:00:00+02', TIMESTAMPTZ '2026-11-28 20:00:00+02', '/img/loc-cs.jpg',
   'FREE', 0, NULL, 'https://www.facebook.com/targuldecraciunarad/', 'primaria@demo.ro')
) AS v(title, description, category, organizer_type, organizer_name, place_id, location_name, address, lat, lng,
       start_at, end_at, image_url, ticket_type, price_from, price_adult, external_url, creator)
WHERE NOT EXISTS (SELECT 1 FROM events e WHERE e.title = v.title AND e.start_at = v.start_at);

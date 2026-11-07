-- V5_1 (T07): un bilet demo TRECUT pentru local@demo.ro, ca „Biletele mele › Trecute” să arate ca în design/final
-- (concert din 18 oct., 2 × Standard, folosit la intrare). Evenimentul e FICTIV (is_demo = true, vezi docs/ARAD-DATA.md).
-- Idempotent: fără id-uri fixe, inserează doar dacă nu există deja.

INSERT INTO events (title, description, category, organizer_type, organizer_name, place_id, location_name, address, lat, lng,
                    start_at, end_at, image_url, ticket_type, price_from, price_adult, capacity, participants_count, saves_count,
                    status, is_demo)
SELECT 'Concert de toamnă: Corul Filarmonicii',
       $$EVENIMENT DEMO. Concert coral de toamnă în Sala Palatului Cultural: muzică românească și europeană, cu Corul Filarmonicii Arad.$$,
       'CONCERT', 'EXTERNAL', 'Filarmonica Arad', 2, 'Palatul Cultural', 'Piața George Enescu 1', 46.1757, 21.3171,
       '2026-10-18 18:00:00+03', '2026-10-18 19:30:00+03', '/img/loc-pc.jpg', 'IN_APP', 35, 35, 200, 2, 0, 'PUBLISHED', true
WHERE NOT EXISTS (SELECT 1 FROM events WHERE title = 'Concert de toamnă: Corul Filarmonicii');

INSERT INTO tickets (event_id, user_id, qty_adult, qty_child, total, code, status, email, used_at, created_at)
SELECT e.id, u.id, 2, 0, 70, 'AX-T' || e.id || '-3907MQ', 'USED', u.email, '2026-10-18 17:52:00+03', '2026-10-10 12:00:00+03'
FROM events e
JOIN users u ON u.email = 'local@demo.ro'
WHERE e.title = 'Concert de toamnă: Corul Filarmonicii'
  AND NOT EXISTS (SELECT 1 FROM tickets t WHERE t.event_id = e.id AND t.user_id = u.id);

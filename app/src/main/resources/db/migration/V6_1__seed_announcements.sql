-- V6_1 (T09): anunțuri demo ale firmelor FICTIVE din V2_1 (vezi docs/ARAD-DATA.md). Idempotent.
INSERT INTO announcements (id, business_id, title, body, image_url, status, created_at) VALUES
  (1, 1, 'Program prelungit de Black Friday', $$Vineri și sâmbătă suntem deschiși până la 23:00. Cappuccino și prăjitura zilei la masa din curte. (FICTIV)$$, NULL, 'PUBLISHED', '2026-11-05 10:00:00+02'),
  (2, 1, 'Căutăm barista part-time', $$Trimite CV-ul la cafenea sau pe e-mail. Program flexibil, weekend-uri. (FICTIV)$$, NULL, 'PUBLISHED', '2026-11-02 09:00:00+02'),
  (3, 3, 'Degustări în fiecare sâmbătă', $$Din noiembrie, degustările la cramă au loc sâmbăta la 17:00, cu rezervare în aplicație. (FICTIV)$$, NULL, 'PUBLISHED', '2026-11-03 12:00:00+02'),
  (4, 4, 'Miere nouă de salcâm', $$Recolta de anul acesta a ajuns. Ne găsiți sâmbăta în Piața Avram Iancu. (FICTIV)$$, NULL, 'PUBLISHED', '2026-11-04 08:30:00+02')
ON CONFLICT (id) DO NOTHING;

SELECT setval('announcements_id_seq', GREATEST((SELECT MAX(id) FROM announcements), 1));

-- V4_2 (T05/T06): pozele reale ale obiectivelor (Wikimedia Commons, cu licență) din designul final.
-- Fișierele: AradEx-Frontend/public/img/loc-*.jpg (copiate din AradEx-Plan/design/assets/, surse în CREDITS-foto-obiective.json).
-- Podgoria Miniș (id 11) nu are încă o poză liberă. Evenimentele fără poză proprie primesc poza sălii/locului (place_id).

UPDATE places SET image_url = v.url, image_credit = v.credit
FROM (VALUES
  (1,  '/img/loc-ca.jpg', 'Foto: Vasile Topac · CC BY-SA 3.0 RO · Wikimedia Commons'),
  (2,  '/img/loc-pc.jpg', 'Foto: Roland Meier · CC BY-SA 2.5 · Wikimedia Commons'),
  (3,  '/img/loc-tc.jpg', 'Foto: B. Proksch · CC BY-SA 2.5 · Wikimedia Commons'),
  (4,  '/img/loc-pa.jpg', 'Foto: Wikimedia Commons · CC BY-SA 3.0'),
  (5,  '/img/loc-br.jpg', 'Foto: Roland Meier · CC BY-SA 2.5 · Wikimedia Commons'),
  (6,  '/img/loc-cs.jpg', 'Foto: tamba lucian · CC BY-SA 3.0 · Wikimedia Commons'),
  (7,  '/img/loc-cc.jpg', 'Foto: IuliuSturza92 · CC BY-SA 4.0 · Wikimedia Commons'),
  (8,  '/img/loc-ta.jpg', 'Foto: Fred Romero · CC BY 2.0 · Wikimedia Commons'),
  (9,  '/img/loc-fm.jpg', 'Foto: bogdymol · CC BY-SA 4.0 · Wikimedia Commons'),
  (10, '/img/loc-ce.jpg', 'Foto: Oguszt · CC BY-SA 3.0 · Wikimedia Commons'),
  (12, '/img/loc-mc.jpg', 'Foto: Radufan · CC BY-SA 3.0 · Wikimedia Commons')
) AS v(id, url, credit)
WHERE places.id = v.id;

UPDATE events e SET image_url = p.image_url
FROM places p
WHERE e.place_id = p.id AND e.image_url IS NULL AND p.image_url IS NOT NULL;

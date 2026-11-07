-- V5_2 (T06/T07, date reale): coordonatele sălilor din V4_1, aliniate la obiectivele corectate în V3_2
-- (verificate pe OpenStreetMap + visitarad.ro; erau deplasate 200 m – 4 km). Vezi AradEx-Plan/docs/ARAD-DATA.md.
-- Doar rândurile din seed (id 1–30 sau is_demo) care au încă coordonatele vechi exacte; evenimentele reale create din panou nu se ating.
-- Număr V5_2 (nu V4_3): trebuie să ruleze și după V5_1 (biletul demo din 18 oct., creat cu coordonatele vechi ale Palatului Cultural).
-- Firmele FICTIVE (Cafeneaua din Pasaj, Bistro La Mureș, Crama Dealul Miniș) își păstrează adresele inventate.

UPDATE events e SET lat = v.new_lat, lng = v.new_lng
FROM (VALUES
  (46.1757, 21.3171, 46.1742, 21.3207),  -- Sala Palatului Cultural (Filarmonica, Complexul Muzeal)
  (46.1726, 21.3153, 46.1711, 21.3158),  -- Teatrul Clasic „Ioan Slavici”
  (46.1690, 21.3180, 46.1832, 21.3240),  -- Piața Catedralei (Târgul de Crăciun), lângă Catedrala „Sfânta Treime”
  (46.1722, 21.3158, 46.1701, 21.3162),  -- Piața Avram Iancu
  (46.1900, 21.2650, 46.1579, 21.2734),  -- Centrul de vizitare Ceala
  (46.1738, 21.3250, 46.1603, 21.3223)   -- Podul Traian (plimbarea „Cetatea văzută de pe Faleză”)
) AS v(old_lat, old_lng, new_lat, new_lng)
WHERE (e.id BETWEEN 1 AND 30 OR e.is_demo) AND e.lat = v.old_lat AND e.lng = v.old_lng;

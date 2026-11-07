-- T22 · transport public CTP Arad: liniile, stațiile și variantele de traseu (date statice, seed în V12_1).
-- Datele live (vehicule, sosiri) NU stau în DB: vin din API-ul public Telelink, prin cache-ul din TransitLiveService.
-- id-urile liniilor și stațiilor sunt cele din Telelink (routeId ≠ numărul afișat, de aceea short_name e separat).

CREATE TABLE transit_routes (
    id          integer PRIMARY KEY,
    short_name  varchar(20)  NOT NULL,
    long_name   varchar(200) NOT NULL,
    mode        varchar(10)  NOT NULL CHECK (mode IN ('TRAM', 'BUS')),
    color       varchar(7),
    text_color  varchar(7)
);

CREATE TABLE transit_stops (
    id    integer PRIMARY KEY,
    name  varchar(120) NOT NULL,
    code  varchar(20),
    lat   double precision NOT NULL,
    lng   double precision NOT NULL
);

-- o variantă de traseu (sens / buclă) a unei linii; pattern_index vine din Telelink (poate fi negativ, unic doar în linie)
CREATE TABLE transit_patterns (
    id             serial PRIMARY KEY,
    route_id       integer NOT NULL REFERENCES transit_routes (id) ON DELETE CASCADE,
    pattern_index  integer NOT NULL,
    from_stop_id   integer REFERENCES transit_stops (id),
    to_stop_id     integer REFERENCES transit_stops (id),
    path           text    NOT NULL,   -- polilinie codificată (Google, precizie 5), simplificată la ~5 m
    UNIQUE (route_id, pattern_index)
);

-- opririle ordonate ale unei variante (o stație poate apărea de două ori într-o buclă)
CREATE TABLE transit_pattern_stops (
    pattern_id  integer NOT NULL REFERENCES transit_patterns (id) ON DELETE CASCADE,
    seq         integer NOT NULL,
    stop_id     integer NOT NULL REFERENCES transit_stops (id),
    PRIMARY KEY (pattern_id, seq)
);

CREATE INDEX ix_transit_pattern_stops_stop ON transit_pattern_stops (stop_id);

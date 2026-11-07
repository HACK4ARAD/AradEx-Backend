package aradexperience.service.transit;

import aradexperience.api.TransitApi;
import aradexperience.dto.transit.StopArrivalsDto;
import aradexperience.dto.transit.TransitArrivalDto;
import aradexperience.dto.transit.TransitRouteDto;
import aradexperience.dto.transit.TransitStopDto;
import aradexperience.dto.transit.TransitVehicleDto;
import aradexperience.dto.transit.TransitVehiclesDto;
import aradexperience.entity.transit.TransitPattern;
import aradexperience.entity.transit.TransitRoute;
import aradexperience.entity.transit.TransitStop;
import aradexperience.exceptions.BadRequestException;
import aradexperience.exceptions.NotFoundException;
import aradexperience.repository.transit.TransitPatternRepository;
import aradexperience.repository.transit.TransitRouteRepository;
import aradexperience.repository.transit.TransitStopRepository;
import aradexperience.util.Geo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static aradexperience.mapper.transit.TransitMapper.expectedAt;
import static aradexperience.mapper.transit.TransitMapper.toArrivalDto;
import static aradexperience.mapper.transit.TransitMapper.toRouteDto;
import static aradexperience.mapper.transit.TransitMapper.toStopDto;
import static aradexperience.mapper.transit.TransitMapper.toVehicleDto;
import static aradexperience.mapper.transit.TransitMapper.utc;

/**
 * Transport public CTP (T22). Rețeaua statică (V12_1) se citește o dată din DB și stă în memorie (nu se schimbă la
 * rulare); datele live vin din {@link TransitLiveService}. Nicio metodă nu aruncă din cauza sursei live: fără ea,
 * răspunsul are `live=false`, fără vehicule / sosiri, dar cu stația și liniile ei.
 */
@Slf4j
@Service
public class TransitService {

    static final int MAX_ARRIVALS = 8;
    /** Sosirile „trecute” de până la 1 min încă se arată (tramvaiul e în stație). */
    static final Duration ARRIVAL_GRACE = Duration.ofMinutes(1);
    /** Peronul de vizavi: aceeași denumire, la cel mult 400 m de stația cea mai apropiată. */
    static final double SAME_STOP_KM = 0.4;
    static final int MAX_PLATFORMS = 3;

    record Network(Map<Integer, TransitRoute> routes, Map<Integer, TransitStop> stops, Map<Integer, TransitStopDto> stopDtos,
                   List<TransitStopDto> stopList, List<TransitRouteDto> routeList) {}

    private final TransitRouteRepository routes;
    private final TransitStopRepository stops;
    private final TransitPatternRepository patterns;
    private final TransitLiveService live;
    private final Clock clock;
    private volatile Network network;

    @Autowired
    public TransitService(TransitRouteRepository routes, TransitStopRepository stops, TransitPatternRepository patterns,
                          TransitLiveService live) {
        this(routes, stops, patterns, live, Clock.systemUTC());
    }

    TransitService(TransitRouteRepository routes, TransitStopRepository stops, TransitPatternRepository patterns,
                   TransitLiveService live, Clock clock) {
        this.routes = routes;
        this.stops = stops;
        this.patterns = patterns;
        this.live = live;
        this.clock = clock;
    }

    public List<TransitStopDto> stops() {
        return network().stopList();
    }

    public List<TransitRouteDto> routes() {
        return network().routeList();
    }

    public TransitVehiclesDto vehicles() {
        Network n = network();
        TransitLiveService.Live<List<Telelink.Vehicle>> l = live.vehicles();
        Instant now = clock.instant();
        List<TransitVehicleDto> list = !l.live() || l.data() == null ? List.of() : l.data().stream()
                .filter(v -> v.routeId() != null && v.latitude() != null && v.longitude() != null)
                .map(v -> toVehicleDto(v, n.routes().get(v.routeId()), now))
                .toList();
        return new TransitVehiclesDto(l.live(), utc(l.fetchedAt()), TransitApi.ATTRIBUTION, list);
    }

    public StopArrivalsDto arrivals(int stopId) {
        Network n = network();
        TransitStopDto stop = n.stopDtos().get(stopId);
        if (stop == null) throw NotFoundException.of("Stop", stopId);
        return arrivalsFor(n, stop, List.of(stopId), null);
    }

    public StopArrivalsDto nearest(Double lat, Double lng) {
        if (lat == null || lng == null || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new BadRequestException("lat and lng are required (WGS84 degrees)");
        }
        Network n = network();
        Function<TransitStop, Double> dist = s -> Geo.distanceKm(lat, lng, s.getLat(), s.getLng());
        TransitStop best = n.stops().values().stream().min(Comparator.comparing(dist)).orElseThrow(() -> NotFoundException.of("Stop", 0));
        String key = best.getName().trim().toLowerCase(Locale.ROOT);
        List<Integer> platforms = n.stops().values().stream()
                .filter(s -> s.getName().trim().toLowerCase(Locale.ROOT).equals(key))
                .filter(s -> Geo.distanceKm(best.getLat(), best.getLng(), s.getLat(), s.getLng()) <= SAME_STOP_KM)
                .sorted(Comparator.comparing(dist))
                .limit(MAX_PLATFORMS)
                .map(TransitStop::getId)
                .toList();
        return arrivalsFor(n, n.stopDtos().get(best.getId()), platforms, Geo.round1(dist.apply(best)));
    }

    private StopArrivalsDto arrivalsFor(Network n, TransitStopDto stop, List<Integer> stopIds, Double distanceKm) {
        boolean anyLive = false;
        Instant updated = null;
        Instant now = clock.instant();
        List<TransitArrivalDto> out = new ArrayList<>();
        for (int id : stopIds) {
            TransitLiveService.Live<List<Telelink.StopTimes>> l = live.stopTimes(id);
            if (!l.live() || l.data() == null) continue; // rezervă: fără ore vechi, care ar minți
            anyLive = true;
            if (updated == null || l.fetchedAt().isAfter(updated)) updated = l.fetchedAt();
            for (Telelink.StopTimes g : l.data()) {
                if (g.route() == null || g.route().routeId() == null || g.times() == null) continue;
                int routeId = g.route().routeId();
                for (Telelink.Time t : g.times()) {
                    if (t.scheduledArrival() == null || expectedAt(t).isBefore(now.minus(ARRIVAL_GRACE))) continue;
                    out.add(toArrivalDto(t, routeId, n.routes().get(routeId), id, now));
                }
            }
        }
        out.sort(Comparator.comparing(TransitArrivalDto::at));
        return new StopArrivalsDto(stop, distanceKm, anyLive, utc(updated), TransitApi.ATTRIBUTION,
                out.stream().limit(MAX_ARRIVALS).toList());
    }

    /** Rețeaua statică, citită o singură dată (prima cerere); seed-ul nu se schimbă cât rulează aplicația. */
    Network network() {
        Network n = network;
        if (n != null) return n;
        synchronized (this) {
            if (network == null) network = load();
            return network;
        }
    }

    private Network load() {
        Map<Integer, TransitRoute> routeMap = routes.findAll().stream()
                .sorted(Comparator.comparing(TransitRoute::getId))
                .collect(Collectors.toMap(TransitRoute::getId, r -> r, (a, b) -> a, LinkedHashMap::new));
        Map<Integer, TransitStop> stopMap = stops.findAll().stream()
                .sorted(Comparator.comparing(TransitStop::getId))
                .collect(Collectors.toMap(TransitStop::getId, s -> s, (a, b) -> a, LinkedHashMap::new));
        List<TransitPattern> all = patterns.findAll();
        Map<Integer, TreeSet<Integer>> routesByStop = new LinkedHashMap<>();
        for (TransitPattern p : all) {
            for (Integer s : p.getStopIds()) routesByStop.computeIfAbsent(s, k -> new TreeSet<>()).add(p.getRouteId());
        }
        Map<Integer, TransitStopDto> stopDtos = new LinkedHashMap<>();
        stopMap.values().forEach(s -> stopDtos.put(s.getId(),
                toStopDto(s, List.copyOf(routesByStop.getOrDefault(s.getId(), new TreeSet<>())))));
        Map<Integer, List<TransitPattern>> byRoute = all.stream().collect(Collectors.groupingBy(TransitPattern::getRouteId));
        List<TransitRouteDto> routeList = routeMap.values().stream()
                .sorted(Comparator.comparing(TransitRoute::getMode).thenComparing(r -> sortKey(r.getShortName())))
                .map(r -> toRouteDto(r, byRoute.getOrDefault(r.getId(), List.of())))
                .toList();
        log.info("Transit network loaded: {} routes, {} stops, {} patterns", routeMap.size(), stopMap.size(), all.size());
        return new Network(routeMap, stopMap, stopDtos, List.copyOf(stopDtos.values()), routeList);
    }

    /** „1”, „1b”, „3”, „15” … în ordine numerică, apoi liniile cu nume („GH - POD”). */
    static String sortKey(String shortName) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+)(.*)").matcher(shortName);
        return m.matches() ? String.format("%05d%s", Integer.parseInt(m.group(1)), m.group(2)) : "~" + shortName;
    }
}

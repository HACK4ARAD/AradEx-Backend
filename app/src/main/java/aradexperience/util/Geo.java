package aradexperience.util;

/** Distanțe pe sferă (haversine). Centrul Aradului ca fallback pentru „lângă mine”. */
public final class Geo {

    public static final double ARAD_LAT = 46.1866;
    public static final double ARAD_LNG = 21.3123;
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private Geo() {}

    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }

    /** Rotunjire la o zecimală, pentru DTO-uri ("0,4 km"). */
    public static double round1(double km) {
        return Math.round(km * 10.0) / 10.0;
    }
}

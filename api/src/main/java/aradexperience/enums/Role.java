package aradexperience.enums;

/**
 * Rolurile aplicației (docs/FEATURES.md §1). ADMIN are acces peste tot (vezi SecurityConfig).
 * Înregistrare liberă doar pentru VISITOR, LOCAL, BUSINESS ({@link #selfRegistrable()}); CITY_HALL și ADMIN vin din seed,
 * VOLUNTEER e creat de Primărie (T18 — coloana `users.role` acceptă valoarea după migrarea V10).
 */
public enum Role {
    VISITOR, LOCAL, BUSINESS, CITY_HALL, ADMIN, VOLUNTEER;

    public boolean selfRegistrable() {
        return this == VISITOR || this == LOCAL || this == BUSINESS;
    }
}

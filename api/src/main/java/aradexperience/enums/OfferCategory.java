package aradexperience.enums;

/**
 * Categoria unei oferte (`offers.category`, T10): cercurile din tab-ul Oferte — Cafenele, Restaurante, Făcut în Arad, Evenimente.
 * „Cu puncte” nu e categorie, ci filtrul `points=true` (oferte cu `pointsPrice`). La creare, implicit din categoria firmei.
 */
public enum OfferCategory {
    CAFE, RESTAURANT, LOCAL, EVENT, OTHER;

    public static OfferCategory defaultFor(BusinessCategory c) {
        return switch (c) {
            case CAFE -> CAFE;
            case RESTAURANT -> RESTAURANT;
            case PRODUCER, WINERY -> LOCAL;
            default -> OTHER;
        };
    }
}

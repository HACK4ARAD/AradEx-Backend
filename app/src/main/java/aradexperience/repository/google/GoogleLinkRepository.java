package aradexperience.repository.google;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Coloana `google_place_id` de pe `places` (V18) și `businesses` (V6), citită și scrisă direct (JDBC), ca T17 să nu
 * modifice entitățile `Place` / `Business` ale lui T05 / T09. Doar ce îi trebuie căutării automate și legăturii.
 */
@Repository
@RequiredArgsConstructor
public class GoogleLinkRepository {

    /** Un obiectiv: numele, adresa și coordonatele (pentru căutarea pe Google) + ID-ul Google, dacă e legat. */
    public record PlaceRow(long id, String name, String address, double lat, double lng, String googlePlaceId) {}

    /** O firmă + ID-ul Google, dacă și l-a legat. */
    public record BusinessRow(long id, String name, String googlePlaceId) {}

    private final JdbcTemplate jdbc;

    public Optional<PlaceRow> place(long id) {
        return jdbc.query("SELECT id, name, address, lat, lng, google_place_id FROM places WHERE id = ?",
                (rs, i) -> new PlaceRow(rs.getLong("id"), rs.getString("name"), rs.getString("address"),
                        rs.getDouble("lat"), rs.getDouble("lng"), rs.getString("google_place_id")), id)
                .stream().findFirst();
    }

    public void setPlaceGoogleId(long id, String googlePlaceId) {
        jdbc.update("UPDATE places SET google_place_id = ? WHERE id = ?", googlePlaceId, id);
    }

    public Optional<BusinessRow> business(long id) {
        return jdbc.query("SELECT id, name, google_place_id FROM businesses WHERE id = ?", this::businessRow, id)
                .stream().findFirst();
    }

    public Optional<BusinessRow> businessOfOwner(long userId) {
        return jdbc.query("SELECT id, name, google_place_id FROM businesses WHERE owner_user_id = ?", this::businessRow, userId)
                .stream().findFirst();
    }

    public void setBusinessGoogleId(long id, String googlePlaceId) {
        jdbc.update("UPDATE businesses SET google_place_id = ? WHERE id = ?", googlePlaceId, id);
    }

    private BusinessRow businessRow(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new BusinessRow(rs.getLong("id"), rs.getString("name"), rs.getString("google_place_id"));
    }
}

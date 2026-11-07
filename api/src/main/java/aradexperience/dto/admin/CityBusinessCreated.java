package aradexperience.dto.admin;

/**
 * docs/API.md §Admin (T14): răspunsul la `POST /city/businesses`. Invitația pe e-mail e simulată, așa că parola de unică folosință
 * vine o singură dată în răspuns (panoul o arată ca s-o poți transmite); nu se mai poate citi după aceea.
 */
public record CityBusinessCreated(CityBusinessDto business, String invitePassword) {
}

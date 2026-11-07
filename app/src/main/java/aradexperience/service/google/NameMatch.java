package aradexperience.service.google;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Verifică dacă locul găsit pe Google e chiar obiectivul nostru: cele două nume trebuie să aibă măcar un cuvânt
 * „cu sens” comun (fără diacritice, primele 4 litere, ca „Băncii” ≈ „Banca”, fără cuvinte generice ca „palatul”, „muzeul”,
 * „arad”). Căutarea pe Google întoarce cel mai relevant loc din zonă chiar dacă nu e cel căutat (ex. „Casa Comitatului” →
 * „Hotel Continental Forum”), iar recenziile altui loc ar fi mai rău decât niciunele.
 */
final class NameMatch {

    private static final Set<String> GENERIC = Set.of(
            "palatul", "palat", "biserica", "catedrala", "catedrală", "manastirea", "muzeul", "muzeu", "casa", "statuia",
            "bustul", "monumentul", "ansamblul", "parcul", "restaurant", "restaurantul", "cafe", "cafenea", "cafeneaua",
            "pizzeria", "pizza", "crama", "cofetaria", "strandul", "strand", "hotel", "teatrul", "arad", "aradului",
            "arad.", "lui", "din", "pentru", "memorial", "memoriala", "sfantul", "sfintii", "and", "the");

    private NameMatch() {}

    static boolean similar(String ours, String google) {
        if (ours == null || google == null) return false;
        Set<String> a = stems(ours);
        Set<String> b = stems(google);
        if (a.isEmpty() || b.isEmpty()) return normalize(ours).equals(normalize(google));
        return a.stream().anyMatch(b::contains);
    }

    private static Set<String> stems(String name) {
        return Arrays.stream(normalize(name).split(" "))
                .filter(w -> w.length() >= 4 && !GENERIC.contains(w))
                .map(w -> w.substring(0, 4))
                .collect(Collectors.toSet());
    }

    private static String normalize(String s) {
        String noMarks = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noMarks.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}

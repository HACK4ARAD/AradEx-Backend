package aradexperience.mock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Cererile pentru testele de controller stau ca JSON în `src/test/resources/requests/<feature>/*.json`
 * (ex. `requests/auth/register.json`), nu inline în teste: `mvc.perform(post(...).content(JsonFixtures.read("requests/auth/register.json")))`.
 */
public final class JsonFixtures {

    private JsonFixtures() {}

    public static String read(String path) {
        try (InputStream in = JsonFixtures.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalArgumentException("missing test resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

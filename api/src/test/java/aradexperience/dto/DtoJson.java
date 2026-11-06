package aradexperience.dto;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Suport pentru testele de serializare: același ObjectMapper ca în Spring Boot (module Java Time etc.) + citire din resources. */
public final class DtoJson {

    public static final ObjectMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

    private DtoJson() {}

    /** Citește `src/test/resources/<path>` (ex. `dto/auth/user-dto.json`). */
    public static String read(String path) {
        try (InputStream in = DtoJson.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalArgumentException("missing test resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T> T fromResource(String path, Class<T> type) {
        try {
            return MAPPER.readValue(read(path), type);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

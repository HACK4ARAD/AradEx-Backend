package aradexperience.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;

/**
 * Adresa clientului în spatele ALB-ului: ALB adaugă adresa văzută de el la FINALUL lui X-Forwarded-For (valorile de la
 * început le poate trimite oricine, deci nu contează); fără antet, adresa conexiunii.
 */
@UtilityClass
public class ClientIp {

    public static String of(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            String[] parts = fwd.split(",");
            return parts[parts.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}

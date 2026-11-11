package aradexperience.config;

import aradexperience.exceptions.ForbiddenException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Set;

/**
 * „Doar demo” (11 nov): până la lansare aplicația e doar de vizualizare. Orice cerere care modifică ceva (POST, PUT, PATCH,
 * DELETE) sub /api/v1 primește 403, de la oricine: conturi demo sau anonim, inclusiv asistentul AI, biletele, înscrierile și
 * panourile. Excepții: autentificarea (/auth/**: login-ul conturilor demo; contul nou și recuperarea răspund singure cu
 * „Not live yet”) și webhook-ul Stripe. Citirile (GET) merg ca înainte. Excepția aruncată aici ajunge la handler-ul global
 * ca ProblemDetail, ca orice 403.
 */
@Configuration
@RequiredArgsConstructor
public class DemoReadOnly implements WebMvcConfigurer {

    public static final String READ_ONLY = "Demo mode: changes are disabled until launch";
    static final Set<String> READS = Set.of("GET", "HEAD", "OPTIONS");

    private final AppProps props;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
                if (props.demoOnly() && !READS.contains(req.getMethod())) throw new ForbiddenException(READ_ONLY);
                return true;
            }
        }).addPathPatterns("/api/v1/**").excludePathPatterns("/api/v1/auth/**", "/api/v1/payments/stripe/webhook");
    }
}

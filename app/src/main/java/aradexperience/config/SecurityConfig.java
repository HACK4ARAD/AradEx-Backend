package aradexperience.config;

import aradexperience.enums.Role;
import aradexperience.security.JwtAuthFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * Regulile de acces pentru TOATE rutele din docs/API.md (coloana „Auth”), scrise o dată în T03 ca celelalte story-uri
 * să nu mai atingă fișierul. Ordinea contează: prima regulă care se potrivește câștigă, deci excepțiile stau înaintea wildcard-urilor.
 * ADMIN are acces pe toate rutele cu rol. Verificările de proprietar (creatorul evenimentului, organizatorul biletului,
 * firma ofertei) se fac în service, cu {@code ForbiddenException}.
 * Fără token / token invalid → 401; rol greșit → 403; ambele ca ProblemDetail.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final String API = "/api/v1";

    // derivate din enum: o redenumire în Role nu poate lăsa o regulă să compare cu un nume vechi
    private static final String VISITOR = Role.VISITOR.name();
    private static final String LOCAL = Role.LOCAL.name();
    private static final String BUSINESS = Role.BUSINESS.name();
    private static final String CITY_HALL = Role.CITY_HALL.name();
    private static final String ADMIN = Role.ADMIN.name();
    private static final String VOLUNTEER = Role.VOLUNTEER.name();

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter, AppProps props, ObjectMapper json) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource(props)))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> problem(json, req, res, HttpStatus.UNAUTHORIZED, "Authentication required"))
                .accessDeniedHandler((req, res, ex) -> problem(json, req, res, HttpStatus.FORBIDDEN, "Access denied")))
            .authorizeHttpRequests(auth -> auth
                // infrastructură: pagina internă de eroare (/error) și preflight-ul CORS. FORWARD NU e permis: un forward
                // spre o rută protejată trece tot prin regulile de mai jos.
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/error", "/actuator/health").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()

                // T03 auth (+ T18 POST /auth/volunteer cu codul de 6 cifre): public; /me/** cere doar login (regula finală)
                .requestMatchers(API + "/health", API + "/auth/**").permitAll()

                // panouri pe roluri (T05/T09/T11/T18 Primărie: /city/places, /city/businesses, /city/actions, /city/volunteers…;
                // T09/T10 firmă; T14 admin; T18 voluntar în aplicație: /vol/actions, /vol/scan, /vol/messages)
                .requestMatchers(API + "/admin/**").hasRole(ADMIN)
                .requestMatchers(API + "/city/**").hasAnyRole(CITY_HALL, ADMIN)
                .requestMatchers(API + "/business/**").hasAnyRole(BUSINESS, ADMIN)
                .requestMatchers(API + "/vol/**").hasAnyRole(VOLUNTEER, ADMIN)

                // T06: doar Primăria creează evenimente oficiale (firmele au /business/events); PUT/DELETE = creator sau ADMIN (în service)
                .requestMatchers(HttpMethod.POST, API + "/events").hasAnyRole(CITY_HALL, ADMIN)
                // T11: înscrierea la acțiuni comunitare e pentru localnici (și vizitatori)
                .requestMatchers(HttpMethod.POST, API + "/community/actions/*/register").hasAnyRole(LOCAL, VISITOR, ADMIN)
                // T05: TTS (opțional, server-side) consumă o cheie plătită → doar logat (excepție înainte de POST /ai/** public)
                .requestMatchers(HttpMethod.POST, API + "/ai/tts").authenticated()

                // citire publică (conținutul personalizat — saved, myRedemption, me — vine din AuthContext.current())
                .requestMatchers(HttpMethod.GET,
                        API + "/places/**", API + "/events/**", API + "/offers/**", API + "/community/**",
                        API + "/businesses/**", API + "/itineraries/**").permitAll()
                // T12/T13: chat și planificator publice; salvarea unui itinerariu merge și fără cont
                .requestMatchers(HttpMethod.POST, API + "/ai/chat", API + "/ai/itinerary", API + "/itineraries").permitAll()

                // restul (me/**, save, tickets, reviews, redeem, DELETE register…): orice utilizator logat
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private CorsConfigurationSource corsConfigurationSource(AppProps props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.cors().origins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("Location"));
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    private static void problem(ObjectMapper json, HttpServletRequest req, HttpServletResponse res, HttpStatus status, String detail) throws IOException {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setInstance(URI.create(req.getRequestURI()));
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        json.writeValue(res.getOutputStream(), pd);
    }
}

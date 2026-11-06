package aradexperience.controller;

import aradexperience.config.AppProps;
import aradexperience.config.SecurityConfig;
import aradexperience.security.JwtService;
import org.mockito.Mockito;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Test de controller „felie” (fără DB): `@ControllerTest(XController.class)` = @WebMvcTest + SecurityConfig REAL (rutele × roluri,
 * 401/403 ca ProblemDetail) + AppProps din application.yml + un JwtService mock (cerut de JwtAuthFilter).
 * În test mai rămâne doar `@MockBean XService` și, pentru rute protejate, `.with(AuthMock.as(id, rol))`.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@WebMvcTest
@Import({SecurityConfig.class, ControllerTest.SecurityTestBeans.class})
@EnableConfigurationProperties(AppProps.class)
public @interface ControllerTest {

    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] value() default {};

    @TestConfiguration
    class SecurityTestBeans {
        @Bean
        JwtService jwtService() {
            return Mockito.mock(JwtService.class);
        }
    }
}

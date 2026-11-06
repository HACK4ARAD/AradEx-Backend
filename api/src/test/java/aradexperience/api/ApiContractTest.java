package aradexperience.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gardă pe disciplina contractului: fiecare interfață din `apis` are @RequestMapping pe tip, fiecare metodă are un
 * mapping, și nu există două metode cu aceeași rută + verb (ar da conflict la pornirea aplicației).
 */
class ApiContractTest {

    @Test
    void everyApiInterface_hasTypeMapping_andEveryMethodHasRoute() throws Exception {
        List<Class<?>> apis = findApiInterfaces();
        assertThat(apis).isNotEmpty();
        Set<String> routes = new HashSet<>();
        for (Class<?> api : apis) {
            RequestMapping type = AnnotatedElementUtils.findMergedAnnotation(api, RequestMapping.class);
            assertThat(type).as("%s needs @RequestMapping", api.getSimpleName()).isNotNull();
            String base = type.path().length > 0 ? type.path()[0] : "";
            for (Method m : api.getDeclaredMethods()) {
                RequestMapping rm = AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
                assertThat(rm).as("%s.%s needs @GetMapping/@PostMapping/...", api.getSimpleName(), m.getName()).isNotNull();
                String path = rm.path().length > 0 ? rm.path()[0] : "";
                for (var verb : rm.method()) {
                    assertThat(routes.add(verb + " " + base + path)).as("duplicate route %s %s%s", verb, base, path).isTrue();
                }
            }
        }
    }

    private static List<Class<?>> findApiInterfaces() throws ClassNotFoundException {
        var provider = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition bd) {
                return bd.getMetadata().isInterface();
            }
        };
        provider.addIncludeFilter((reader, factory) -> reader.getClassMetadata().getClassName().endsWith("Api"));
        List<Class<?>> result = new ArrayList<>();
        for (BeanDefinition bd : provider.findCandidateComponents(ApiContractTest.class.getPackageName())) {
            result.add(Class.forName(bd.getBeanClassName()));
        }
        return result;
    }
}

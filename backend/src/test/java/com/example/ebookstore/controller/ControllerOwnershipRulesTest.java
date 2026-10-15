package com.example.ebookstore.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.ebookstore.entity.User;

/**
 * Guard rails for ownership, checked on every controller now and on every controller added later:
 * <ul>
 *   <li>no handler method takes a user id from the client (query, header, path or body);</li>
 *   <li>no handler method returns or accepts a JPA entity.</li>
 * </ul>
 * The user must always come from {@code @AuthenticationPrincipal AuthenticatedUser}.
 */
class ControllerOwnershipRulesTest {

    private static final Set<String> USER_ID_NAMES = Set.of("userid", "user_id", "customerid", "ownerid", "x-user-id");

    @Test
    void noControllerAcceptsAUserIdFromTheClient() throws ClassNotFoundException {
        List<String> violations = new ArrayList<>();
        for (Class<?> controller : controllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                for (Parameter parameter : method.getParameters()) {
                    if (USER_ID_NAMES.contains(parameter.getName().toLowerCase())) {
                        violations.add(describe(controller, method) + " parameter " + parameter.getName());
                    }
                    RequestParam param = parameter.getAnnotation(RequestParam.class);
                    if (param != null && USER_ID_NAMES.contains(param.name().toLowerCase())) {
                        violations.add(describe(controller, method) + " @RequestParam " + param.name());
                    }
                    RequestHeader header = parameter.getAnnotation(RequestHeader.class);
                    if (header != null && USER_ID_NAMES.contains(header.name().toLowerCase())) {
                        violations.add(describe(controller, method) + " @RequestHeader " + header.name());
                    }
                    if (parameter.isAnnotationPresent(RequestBody.class)) {
                        for (var component : parameter.getType().isRecord()
                                ? parameter.getType().getRecordComponents()
                                : new java.lang.reflect.RecordComponent[0]) {
                            if (USER_ID_NAMES.contains(component.getName().toLowerCase())) {
                                violations.add(describe(controller, method) + " body field " + component.getName());
                            }
                        }
                    }
                }
            }
        }
        assertThat(violations).as("client-supplied user ids").isEmpty();
    }

    @Test
    void controllersNeverExposeJpaEntities() throws ClassNotFoundException {
        List<String> violations = new ArrayList<>();
        for (Class<?> controller : controllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                if (mentionsEntity(method.getGenericReturnType().getTypeName())) {
                    violations.add(describe(controller, method) + " returns an entity");
                }
                for (Parameter parameter : method.getParameters()) {
                    if (mentionsEntity(parameter.getParameterizedType().getTypeName())) {
                        violations.add(describe(controller, method) + " takes an entity");
                    }
                }
            }
        }
        assertThat(violations).isEmpty();
        assertThat(User.class.getPackageName()).isEqualTo("com.example.ebookstore.entity");
    }

    private static boolean mentionsEntity(String typeName) {
        return typeName.contains("com.example.ebookstore.entity.") && !typeName.endsWith(".Role");
    }

    private static String describe(Class<?> controller, Method method) {
        return controller.getSimpleName() + "." + method.getName();
    }

    private static List<Class<?>> controllers() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> result = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.example.ebookstore.controller")) {
            result.add(Class.forName(definition.getBeanClassName()));
        }
        assertThat(result).as("controllers found").isNotEmpty();
        return result;
    }
}

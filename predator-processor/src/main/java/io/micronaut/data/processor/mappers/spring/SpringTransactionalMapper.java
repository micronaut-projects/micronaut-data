package io.micronaut.data.processor.mappers.spring;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Maps Spring's transaction annotation.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class SpringTransactionalMapper implements NamedAnnotationMapper {

    @Override
    public final String getName() {
        return "org.springframework.transaction.annotation.Transactional";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        final AnnotationValueBuilder<?> builder = AnnotationValue.builder("io.micronaut.spring.tx.annotation.Transactional");
        annotation.getValue(String.class).ifPresent(s -> {
            builder.value(s);
            builder.member("transactionManager", s);
        });

        Stream.of("propagation", "isolation", "transactionManager")
                .forEach(member -> annotation.get(member, String.class).ifPresent(s -> builder.member(member, s)));
        Stream.of("rollbackForClassName", "noRollbackForClassName")
                .forEach(member -> annotation.get(member, String[].class).ifPresent(s -> builder.member(member, s)));
        Stream.of("rollbackFor", "noRollbackFor")
                .forEach(member -> annotation.get(member, AnnotationClassValue[].class).ifPresent(classValues -> {
                    String[] names = new String[classValues.length];
                    for (int i = 0; i < classValues.length; i++) {
                        AnnotationClassValue classValue = classValues[i];
                        names[i] = classValue.getName();
                    }
                    builder.member(member, names);
                }));
        annotation.get("timeout", Integer.class).ifPresent(integer -> builder.member("timeout", integer));
        annotation.get("readOnly", Boolean.class).ifPresent(bool -> builder.member("readOnly", bool));

        return Collections.singletonList(builder.build());
    }
}

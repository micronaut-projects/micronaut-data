package io.micronaut.data.processor.mappers.spring;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.data.annotation.Repository;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.Nonnull;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * Maps Spring's repository annotation to Micronaut's.
 *
 * @author graemerocher
 * @since 1.0
 */
public class SpringRepositoryMapper implements NamedAnnotationMapper {
    @Nonnull
    @Override
    public String getName() {
        return "org.springframework.stereotype.Repository";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        final AnnotationValueBuilder<Repository> builder = AnnotationValue.builder(Repository.class);
        annotation.stringValue("org.springframework.stereotype.Repository").ifPresent(builder::value);
        return Collections.singletonList(
                builder
                    .build()
        );
    }
}

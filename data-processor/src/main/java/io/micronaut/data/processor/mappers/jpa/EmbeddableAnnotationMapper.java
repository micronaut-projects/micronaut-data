package io.micronaut.data.processor.mappers.jpa;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.processor.mappers.MappedEntityMapper;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.Nonnull;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;

/**
 * Maps JPA Embeddable to Micronaut Data Embedabble.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class EmbeddableAnnotationMapper implements NamedAnnotationMapper {
    @Nonnull
    @Override
    public String getName() {
        return "javax.persistence.Embeddable";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {

        return Arrays.asList(
                MappedEntityMapper.buildIntrospectionConfiguration(),
                AnnotationValue.builder(Embeddable.class).build()
        );
    }
}

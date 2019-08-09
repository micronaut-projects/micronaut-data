package io.micronaut.data.processor.mappers.jpa;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Relation;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.Nonnull;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;

/**
 * Maps JPA's embedded ID to {@link Id}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public final class EmbeddedIdAnnotationMapper implements NamedAnnotationMapper {
    @Nonnull
    @Override
    public String getName() {
        return "javax.persistence.EmbeddedId";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        return Arrays.asList(
                AnnotationValue.builder(Id.class)
                        .build(),
                AnnotationValue.builder(Relation.class)
                        .value(Relation.Kind.EMBEDDED)
                        .build()
        );
    }
}

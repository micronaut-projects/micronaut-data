package io.micronaut.data.processor.mappers.jpa;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.data.annotation.Relation;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.Nonnull;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

public class EmbeddedAnnotationMapper implements NamedAnnotationMapper {
    @Nonnull
    @Override
    public String getName() {
        return "javax.persistence.Embedded";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(
                AnnotationValue.builder(Relation.class)
                    .value(Relation.Kind.EMBEDDED)
                .build()
        );
    }
}

package io.micronaut.data.processor.mappers.jpa;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.data.annotation.Relation;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * Maps JPA's {@code ManyToOne} annotation to {@link Relation}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class ManyToOneMapper implements NamedAnnotationMapper {
    @NonNull
    @Override
    public String getName() {
        return "javax.persistence.ManyToOne";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        AnnotationValue<Relation> ann = AnnotationValue.builder(Relation.class).value(Relation.Kind.MANY_TO_ONE).build();
        return Collections.singletonList(ann);
    }
}

package io.micronaut.data.processor.mappers.jpa;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.data.annotation.Persisted;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps JPA column annotation to {@link Persisted}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public final class ColumnAnnotationMapper implements NamedAnnotationMapper {

    @NonNull
    @Override
    public String getName() {
        return "javax.persistence.Column";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        final String name = annotation.get("name", String.class).orElse(null);
        final boolean nullable = annotation.get("nullable", boolean.class).orElse(false);
        AnnotationValue<Persisted> persistedAnnotationValue;
        List<AnnotationValue<?>> values = new ArrayList<>(2);
        if (name != null) {
            persistedAnnotationValue = AnnotationValue.builder(Persisted.class)
                    .value(name)
                    .build();

            values.add(persistedAnnotationValue);
        }

        if (nullable) {
            values.add(AnnotationValue.builder(Nullable.class).build());
        }

        return values;
    }

}

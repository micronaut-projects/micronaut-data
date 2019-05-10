package io.micronaut.data.processor.mappers.jpa;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.data.annotation.Persisted;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * Maps JPA's {@code Table} annotation to {@link Persisted}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public final class TableAnnotationMapper implements NamedAnnotationMapper {

    @NonNull
    @Override
    public String getName() {
        return "javax.persistence.Table";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        final String name = annotation.get("name", String.class).orElse(null);
        if (name != null) {
            final AnnotationValueBuilder<Persisted> builder = AnnotationValue.builder(Persisted.class);
            builder.value(name);
            return Collections.singletonList(builder.build());
        } else {
            return Collections.emptyList();
        }
    }
}

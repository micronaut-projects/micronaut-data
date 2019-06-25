package io.micronaut.data.processor.mappers.support;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Collections;
import java.util.List;

/**
 * Maps {@link Nullable} to javax nullable.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class NullableAnnotationMapper implements TypedAnnotationMapper<Nullable> {
    @Override
    public Class<Nullable> annotationType() {
        return Nullable.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Nullable> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(
                AnnotationValue.builder("javax.annotation.Nullable").build()
        );
    }
}

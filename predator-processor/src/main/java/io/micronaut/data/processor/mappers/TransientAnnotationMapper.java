package io.micronaut.data.processor.mappers;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import javax.persistence.Transient;
import java.util.Collections;
import java.util.List;

public class TransientAnnotationMapper implements TypedAnnotationMapper<Transient> {
    @Override
    public Class<Transient> annotationType() {
        return Transient.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Transient> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(AnnotationValue.builder(io.micronaut.data.annotation.Transient.class)
                .build());
    }
}

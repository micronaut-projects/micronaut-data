package io.micronaut.data.processor.mappers;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import javax.persistence.GeneratedValue;
import java.util.Collections;
import java.util.List;

public class GeneratedValueMapper implements TypedAnnotationMapper<GeneratedValue> {
    @Override
    public Class<GeneratedValue> annotationType() {
        return GeneratedValue.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<GeneratedValue> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(AnnotationValue.builder(io.micronaut.data.annotation.GeneratedValue.class)
                    .build());
    }
}

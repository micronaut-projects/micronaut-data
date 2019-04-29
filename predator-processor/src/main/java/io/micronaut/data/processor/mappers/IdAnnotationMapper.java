package io.micronaut.data.processor.mappers;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import javax.persistence.Id;
import java.util.Collections;
import java.util.List;

public class IdAnnotationMapper implements TypedAnnotationMapper<Id> {
    @Override
    public Class<Id> annotationType() {
        return Id.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Id> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(
                AnnotationValue.builder(io.micronaut.data.annotation.Id.class)
                    .build()
        );
    }
}

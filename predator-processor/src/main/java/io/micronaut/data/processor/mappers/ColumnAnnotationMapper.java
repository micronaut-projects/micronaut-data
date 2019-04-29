package io.micronaut.data.processor.mappers;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.data.annotation.Persisted;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.Nullable;
import javax.persistence.Column;
import java.util.ArrayList;
import java.util.List;

public class ColumnAnnotationMapper implements TypedAnnotationMapper<Column> {
    @Override
    public Class<Column> annotationType() {
        return Column.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Column> annotation, VisitorContext visitorContext) {
        final String name = annotation.get("name", String.class).orElse(null);
        final boolean nullable = annotation.get("nullable", boolean.class).orElse(false);
        AnnotationValue<Persisted> persistedAnnotationValue = null;
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

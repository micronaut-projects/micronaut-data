package io.micronaut.data.processor.mappers;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.data.annotation.Persisted;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import javax.persistence.Table;
import java.util.Collections;
import java.util.List;

public class TableAnnotationMapper implements TypedAnnotationMapper<Table> {

    @Override
    public Class<Table> annotationType() {
        return Table.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Table> annotation, VisitorContext visitorContext) {
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

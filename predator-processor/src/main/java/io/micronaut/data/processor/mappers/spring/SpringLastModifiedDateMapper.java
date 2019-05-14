package io.micronaut.data.processor.mappers.spring;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

public class SpringLastModifiedDateMapper implements NamedAnnotationMapper {
    @NonNull
    @Override
    public String getName() {
        return "org.springframework.data.annotation.LastModifiedDate";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(
                AnnotationValue.builder(DateUpdated.class).build()
        );
    }
}

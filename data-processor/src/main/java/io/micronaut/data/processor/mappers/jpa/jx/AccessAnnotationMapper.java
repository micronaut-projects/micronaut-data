package io.micronaut.data.processor.mappers.jpa.jx;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

public class AccessAnnotationMapper implements NamedAnnotationMapper {

    static final String INTROSPECTED_ACCESS_KIND = "accessKind";
    static final String INTROSPECTED_VISIBILITY = "visibility";

    enum JavaxAccessType {
        FIELD,
        PROPERTY
    }

    @Override
    public String getName() {
        return "javax.persistence.Access";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        JavaxAccessType accessType = annotation.getRequiredValue("value", JavaxAccessType.class);

        AnnotationValueBuilder<Introspected> introspectedBuilder = AnnotationValue.builder(Introspected.class);

        switch (accessType) {
            case FIELD ->
                introspectedBuilder.member(INTROSPECTED_ACCESS_KIND, new Introspected.AccessKind[]{
                    Introspected.AccessKind.FIELD, Introspected.AccessKind.METHOD
                }).member(INTROSPECTED_VISIBILITY, Introspected.Visibility.ANY);
            case PROPERTY ->
                introspectedBuilder.member(INTROSPECTED_ACCESS_KIND, Introspected.AccessKind.METHOD);
        }
        return Collections.singletonList(introspectedBuilder.build());
    }

}

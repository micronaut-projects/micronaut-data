package io.micronaut.data.processor.mappers.spring;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.Nonnull;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * Maps Spring's TransactionalEventListener to Micronaut's.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class SpringTransactionalEventListenerMapper implements NamedAnnotationMapper {
    @Nonnull
    @Override
    public String getName() {
        return "org.springframework.transaction.event.TransactionalEventListener";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        final AnnotationValueBuilder<Annotation> ann =
                AnnotationValue.builder("io.micronaut.transaction.annotation.TransactionalEventListener");
        annotation.stringValue("phase").ifPresent(ann::value);
        return Collections.singletonList(
                ann.build()
        );
    }
}

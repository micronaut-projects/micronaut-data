package io.micronaut.data.processor.mappers.jta;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * Maps JTA's transaction annotation.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class JtaTransactionalMapper implements NamedAnnotationMapper {
    @NonNull
    @Override
    public String getName() {
        return "javax.transaction.Transactional";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {

        AnnotationValueBuilder<Annotation> builder = AnnotationValue.builder("io.micronaut.spring.tx.annotation.Transactional");
        annotation.getValue(String.class).ifPresent(type ->
            builder.member("propagation", type)
        );
        annotation.get("rollbackOn", String[].class).ifPresent(type ->
                builder.member("rollbackFor", type)
        );
        annotation.get("dontRollbackOn", String[].class).ifPresent(type ->
                builder.member("noRollbackFor", type)
        );
        return Collections.singletonList(
                builder.build()
        );
    }
}

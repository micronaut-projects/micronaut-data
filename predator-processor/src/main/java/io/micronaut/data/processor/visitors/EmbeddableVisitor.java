package io.micronaut.data.processor.visitors;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;

/**
 * A visitor that handles types annotated with {@link Embeddable}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class EmbeddableVisitor implements TypeElementVisitor<Embeddable, Object> {

    private MappedEntityVisitor mappedEntityVisitor = new MappedEntityVisitor(false);

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        mappedEntityVisitor
                .visitClass(element, context);
    }
}

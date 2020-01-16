package io.micronaut.data.processor.visitors.finders.specification;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.AbstractListMethod;
import io.micronaut.data.processor.visitors.finders.MethodMatchInfo;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;

/**
 * Implementation of {@code count(Specification)} for Spring Data JPA specifications.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Internal
public class CountSpecificationMethod extends AbstractListMethod {
    /**
     * The prefixes used.
     */
    public static final String[] PREFIXES = {"count"};

    /**
     * Find one method.
     */
    public CountSpecificationMethod() {
        super(PREFIXES);
    }

    @Override
    public int getOrder() {
        return DEFAULT_POSITION - 200;
    }

    @Override
    public boolean isMethodMatch(@NonNull MethodElement methodElement, @NonNull MatchContext matchContext) {

        final VisitorContext visitorContext = matchContext.getVisitorContext();
        ClassElement returnType = matchContext.getReturnType();

        return super.isMethodMatch(methodElement, matchContext) &&
                (returnType.isPrimitive() || returnType.isAssignable(Number.class)) &&
                visitorContext.getClassElement("io.micronaut.data.spring.jpa.intercept.CountSpecificationInterceptor").isPresent() &&
                visitorContext.getClassElement("org.springframework.data.jpa.domain.Specification").isPresent() &&
                isFirstParameterJpaSpecification(methodElement);
    }

    @Nullable
    @Override
    public MethodMatchInfo buildMatchInfo(@NonNull MethodMatchContext matchContext) {
        final ClassElement interceptorElement = getInterceptorElement(matchContext,
                "io.micronaut.data.spring.jpa.intercept.CountSpecificationInterceptor");
        return new MethodMatchInfo(
                matchContext.getReturnType(),
                null,
                interceptorElement
        );
    }

    private boolean isFirstParameterJpaSpecification(@NonNull MethodElement methodElement) {
        final ParameterElement[] parameters = methodElement.getParameters();
        return parameters.length == 1 &&
                parameters[0].getType().isAssignable("org.springframework.data.jpa.domain.Specification");
    }
}


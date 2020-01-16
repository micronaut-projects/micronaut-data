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
 * Compilation time implementation of {@code Page find(Specification, Pageable)} for Spring Data JPA.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class FindPageSpecificationMethod extends AbstractListMethod {
    /**
     * The prefixes used.
     */
    public static final String[] PREFIXES = {"get", "find", "search", "query"};

    /**
     * Find one method.
     */
    public FindPageSpecificationMethod() {
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
                returnType.isAssignable("org.springframework.data.domain.Page") &&
                visitorContext.getClassElement("io.micronaut.data.spring.jpa.intercept.FindPageSpecificationInterceptor").isPresent() &&
                visitorContext.getClassElement("org.springframework.data.jpa.domain.Specification").isPresent() &&
                areParametersValid(methodElement);
    }

    @Nullable
    @Override
    public MethodMatchInfo buildMatchInfo(@NonNull MethodMatchContext matchContext) {
        final ClassElement interceptorElement = getInterceptorElement(matchContext,
                "io.micronaut.data.spring.jpa.intercept.FindPageSpecificationInterceptor");
        return new MethodMatchInfo(
                matchContext.getReturnType(),
                null,
                interceptorElement
        );
    }

    private boolean areParametersValid(@NonNull MethodElement methodElement) {
        final ParameterElement[] parameters = methodElement.getParameters();
        final int len = parameters.length;
        return len == 2 && parameters[0].getType().isAssignable("org.springframework.data.jpa.domain.Specification") &&
                parameters[1].getType().isAssignable("org.springframework.data.domain.Pageable");
    }
}

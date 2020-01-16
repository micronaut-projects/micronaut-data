package io.micronaut.data.processor.visitors.finders.specification;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.data.processor.visitors.finders.AbstractListMethod;
import io.micronaut.data.processor.visitors.finders.MethodMatchInfo;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;

import java.util.Optional;

/**
 * Spring Data JPA specification findOne.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class FindOneSpecificationMethod extends AbstractListMethod {
    /**
     * The prefixes used.
     */
    public static final String[] PREFIXES = {"get", "find", "search", "query"};

    /**
     * Find one method.
     */
    public FindOneSpecificationMethod() {
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
        if (returnType.isAssignable(Optional.class)) {
            returnType = returnType.getFirstTypeArgument().orElse(returnType);
        }

        return super.isMethodMatch(methodElement, matchContext) &&
                returnType.hasStereotype(MappedEntity.class) &&
                visitorContext.getClassElement("io.micronaut.data.spring.jpa.intercept.FindOneSpecificationInterceptor").isPresent() &&
                visitorContext.getClassElement("org.springframework.data.jpa.domain.Specification").isPresent() &&
                isFirstParameterJpaSpecification(methodElement);
    }

    @Nullable
    @Override
    public MethodMatchInfo buildMatchInfo(@NonNull MethodMatchContext matchContext) {
        final ClassElement interceptorElement = getInterceptorElement(matchContext,
                "io.micronaut.data.spring.jpa.intercept.FindOneSpecificationInterceptor");
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

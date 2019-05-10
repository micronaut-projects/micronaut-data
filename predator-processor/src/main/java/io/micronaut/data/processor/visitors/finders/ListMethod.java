package io.micronaut.data.processor.visitors.finders;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;

import javax.annotation.Nonnull;
import java.util.regex.Pattern;

/**
 * Simple list method support.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class ListMethod extends AbstractListMethod {

    /**
     * The prefixes used.
     */
    public static final String[] PREFIXES = {"list", "find", "search", "query"};

    /**
     * Default constructor.
     */
    public ListMethod() {
        super(
                PREFIXES
        );
    }

    @Override
    public int getOrder() {
        // lower priority than dynamic finder
        return DEFAULT_POSITION + 100;
    }

    @Override
    public boolean isMethodMatch(MethodElement methodElement) {
        ClassElement returnType = methodElement.getGenericReturnType();
        return super.isMethodMatch(methodElement) && isValidReturnTypeInternal(returnType);
    }

    /**
     * Dictates whether this is a valid return type.
     * @param returnType The return type.
     * @return True if it is
     */
    protected boolean isValidReturnType(@Nonnull ClassElement returnType) {
        return returnType.isAssignable(Iterable.class);
    }

    private boolean isValidReturnTypeInternal(ClassElement returnType) {
        return returnType != null &&
                isValidReturnType(returnType);
    }

}

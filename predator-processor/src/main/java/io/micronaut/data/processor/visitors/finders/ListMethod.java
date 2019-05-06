package io.micronaut.data.processor.visitors.finders;

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;

import javax.annotation.Nonnull;
import java.util.regex.Pattern;

public class ListMethod extends AbstractListMethod {

    private static final String METHOD_PATTERN = "^((list|find|search|query)(\\S*?))$";

    public ListMethod() {
        super(Pattern.compile(METHOD_PATTERN));
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

    private boolean isValidReturnTypeInternal(ClassElement returnType) {
        return returnType != null &&
                isValidReturnType(returnType);
    }

    /**
     * Dictates whether this is a valid return type.
     * @param returnType The return type.
     * @return True if it is
     */
    protected boolean isValidReturnType(@Nonnull ClassElement returnType) {
        return returnType.isAssignable(Iterable.class) &&
                hasPersistedTypeArgument(returnType);
    }

}

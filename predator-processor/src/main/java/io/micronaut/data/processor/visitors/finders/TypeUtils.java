package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.data.annotation.Persisted;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import org.reactivestreams.Publisher;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;
import java.util.stream.Stream;

@Internal
class TypeUtils {
    static boolean isIterableOfEntity(@Nullable ClassElement type) {
        return type != null && type.isAssignable(Iterable.class) && hasPersistedTypeArgument(type);
    }

    static boolean isEntityContainerType(ClassElement returnType) {
        return isContainerType(returnType)
                && hasPersistedTypeArgument(returnType);
    }

    static boolean hasPersistedTypeArgument(ClassElement returnType) {
        return returnType.getFirstTypeArgument().map(t -> t.hasAnnotation(Persisted.class)).orElse(false);
    }

    /**
     * Does the method return an object convertible to a number.
     * @param methodElement The method element
     * @return True if it does
     */
    static boolean doesReturnNumber(@Nonnull MethodElement methodElement) {
        ClassElement returnType = methodElement.getGenericReturnType();
        if (returnType != null) {
            return isNumber(returnType);
        } else {
            return false;
        }
    }

    static boolean doesReturnVoid(MethodElement methodElement) {
        ClassElement rt = methodElement.getReturnType();
        return rt != null && isVoid(rt);
    }

    static  boolean doesReturnBoolean(MethodElement methodElement) {
        ClassElement returnType = methodElement.getGenericReturnType();
        if (returnType != null) {
            return isBoolean(returnType);
        }
        return false;
    }

    static boolean isContainerType(@NonNull ClassElement returnType) {
        return returnType.isAssignable(Iterable.class) ||
                returnType.isAssignable(Stream.class) ||
                returnType.isAssignable(Publisher.class) ||
                returnType.isAssignable(Optional.class);
    }

    static boolean isNumber(@NonNull ClassElement returnType) {
        if (returnType.isPrimitive()) {
            return ClassUtils.getPrimitiveType(returnType.getName()).map(aClass ->
                    Number.class.isAssignableFrom(ReflectionUtils.getWrapperType(aClass))
            ).orElse(false);
        } else {
            return returnType.isAssignable(Number.class);
        }
    }

    static boolean isVoid(@NonNull ClassElement classElement) {
        return classElement.getName().equals("void");
    }

    static boolean isBoolean(@NonNull ClassElement returnType) {
        return returnType.isAssignable(Boolean.class) || (returnType.isPrimitive() && returnType.getName().equals("boolean"));
    }
}

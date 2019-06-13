/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.model.DataType;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import org.reactivestreams.Publisher;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.temporal.Temporal;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.stream.Stream;

/**
 * Internal utility methods.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@Internal
public class TypeUtils {

    private static final Map<String, DataType> RESOLVED_DATA_TYPES = new HashMap<>(50);

    /**
     * Is the element an iterable of an entity.
     *
     * @param type The type
     * @return True if is
     */
    public static boolean isIterableOfEntity(@Nullable ClassElement type) {
        return type != null && type.isAssignable(Iterable.class) && hasPersistedTypeArgument(type);
    }

    /**
     * Is the given type a container type of entity.
     * @param type The type
     * @return True if it is
     */
    public static boolean isEntityContainerType(@Nullable ClassElement type) {
        return isContainerType(type)
                && hasPersistedTypeArgument(type);
    }

    /**
     * Does the given type have a first argument annotated with {@link MappedEntity}.
     * @param type The type
     * @return True if it does
     */
    public static boolean hasPersistedTypeArgument(@Nullable ClassElement type) {
        if (type == null) {
            return false;
        }
        return type.getFirstTypeArgument().map(t -> t.hasAnnotation(MappedEntity.class)).orElse(false);
    }

    /**
     * Does the method return an object convertible to a number.
     *
     * @param methodElement The method element
     * @return True if it does
     */
    public static boolean doesReturnNumber(@NonNull MethodElement methodElement) {
        ClassElement returnType = methodElement.getGenericReturnType();
        if (returnType != null) {
            return isNumber(returnType);
        } else {
            return false;
        }
    }

    /**
     * Does the method element return void.
     * @param methodElement The method element
     * @return True if it returns void
     */
    public static boolean doesReturnVoid(@NonNull MethodElement methodElement) {
        ClassElement rt = methodElement.getReturnType();
        return isVoid(rt);
    }

    /**
     * Does the given method element return boolean.
     * @param methodElement The method element
     * @return True if it does
     */
    public static boolean doesReturnBoolean(@NonNull MethodElement methodElement) {
        return isBoolean(methodElement.getGenericReturnType());
    }

    /**
     * Is the type a container type such as a collection etc.
     * @param type The type
     * @return True if is
     */
    public static boolean isContainerType(@Nullable ClassElement type) {
        if (type == null) {
            return false;
        }
        return type.isAssignable(Iterable.class) ||
                type.isAssignable(Stream.class) ||
                isReactiveType(type) ||
                type.isAssignable(Optional.class) ||
                isFutureType(type);

    }

    /**
     * Is the type a reactive type.
     * @param type The type
     * @return True if is
     */
    public static boolean isReactiveType(@Nullable ClassElement type) {
        return type != null && (type.isAssignable(Publisher.class) || type.getPackageName().equals("io.reactivex"));
    }

    /**
     * Is the type a future type.
     * @param type The type
     * @return True if is
     */
    public static boolean isFutureType(@Nullable ClassElement type) {
        if (type == null) {
            return false;
        }
        return type.isAssignable(CompletionStage.class) ||
                type.isAssignable(Future.class);
    }

    /**
     * Is the type a future type.
     * @param type The type
     * @return True if is
     */
    public static boolean isReactiveOrFuture(@Nullable ClassElement type) {
        if (type == null) {
            return false;
        }
        return isReactiveType(type) || isFutureType(type);
    }

    /**
     * Is the type a number.
     * @param type The type
     * @return True if is a number
     */
    public static boolean isNumber(@Nullable ClassElement type) {
        if (type == null) {
            return false;
        }
        if (type.isPrimitive()) {
            return ClassUtils.getPrimitiveType(type.getName()).map(aClass ->
                    Number.class.isAssignableFrom(ReflectionUtils.getWrapperType(aClass))
            ).orElse(false);
        } else {
            return type.isAssignable(Number.class);
        }
    }

    /**
     * Is the type void.
     * @param type The type
     * @return True if is void
     */
    public static boolean isVoid(@Nullable ClassElement type) {
        return type != null && type.getName().equals("void");
    }

    /**
     * Is the type a boolean.
     * @param type The type
     * @return True if is a boolean
     */
    public static boolean isBoolean(@Nullable ClassElement type) {
        return type != null &&
                (type.isAssignable(Boolean.class) || (type.isPrimitive() && type.getName().equals("boolean")));
    }

    /**
     * Retruns true if no type argument is present, a void argument is present or a boolean argument is present.
     * @param type The type
     * @return True if the argument is a void argument
     */
    public static boolean isVoidOrNumberArgument(ClassElement type) {
        if (type == null) {
            return false;
        }
        ClassElement ce = type.getFirstTypeArgument().orElse(null);
        return ce == null || ce.isAssignable(Void.class) || isNumber(ce);
    }

    /**
     * Returns true if the return type is considered valid for batch update operations likes deletes and updates.
     * @param methodElement The method element
     * @return True if is valid
     */
    static boolean isValidBatchUpdateReturnType(MethodElement methodElement) {
        return doesReturnVoid(methodElement) ||
                (isReactiveOrFuture(methodElement.getReturnType()) &&
                        isVoidOrNumberArgument(methodElement.getReturnType()));
    }

    /**
     * Whether the given type is Object.
     * @param type The type
     * @return True if it is Object
     */
    public static boolean isObjectClass(ClassElement type) {
        return type != null && type.getName().equals(Object.class.getName());
    }

    /**
     * Compute the data type for the given type.
     * @param type The type
     * @return The data type
     */
    public static @NonNull DataType resolveDataType(@NonNull ClassElement type) {
        final String typeName = type.isArray() ? type.getName() + "[]" : type.getName();

        return RESOLVED_DATA_TYPES.computeIfAbsent(typeName, s -> {
            if (type.isPrimitive() || typeName.startsWith("java.lang")) {
                Class primitiveType = ClassUtils.getPrimitiveType(type.getName()).orElse(null);
                if (primitiveType != null && primitiveType != void.class) {
                    String wrapperName = ReflectionUtils.getWrapperType(primitiveType).getSimpleName();
                    DataType dt = DataType.valueOf(wrapperName.toUpperCase(Locale.ENGLISH));
                    if (type.isArray()) {
                        if (dt == DataType.BYTE) {
                            return DataType.BYTE_ARRAY;
                        }
                    } else {
                        return dt;
                    }
                }
            }

            try {
                if (ClassUtils.isJavaBasicType(type.getName())) {
                    Class pt = ClassUtils.getPrimitiveType(type.getName()).orElse(null);
                    if (pt != null) {
                        String wrapperName = ReflectionUtils.getWrapperType(pt).getSimpleName();
                        return DataType.valueOf(wrapperName.toUpperCase(Locale.ENGLISH));
                    } else {
                        return DataType.valueOf(type.getSimpleName().toUpperCase(Locale.ENGLISH));
                    }
                }
            } catch (IllegalArgumentException e) {
                // ignore
            }

            if (type.isAssignable(CharSequence.class)) {
                return DataType.STRING;
            } else if (type.isAssignable(BigDecimal.class) || type.isAssignable(BigInteger.class)) {
                return DataType.BIGDECIMAL;
            } else if (type.isAssignable(Temporal.class) || type.isAssignable(Date.class)) {
                return DataType.DATE;
            } else if (Stream.of(UUID.class, Charset.class, TimeZone.class, Locale.class, URL.class, URI.class).anyMatch(type::isAssignable)) {
                return DataType.STRING;
            }

            return DataType.OBJECT;
        });

    }
}

/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.Slice;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import org.reactivestreams.Publisher;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Year;
import java.time.YearMonth;
import java.time.chrono.ChronoLocalDate;
import java.time.temporal.Temporal;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
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

    @Nullable
    public static ClassElement getKotlinCoroutineProducedType(@NonNull MethodElement methodElement) {
        if (!methodElement.isSuspend()) {
            throw new IllegalStateException("Not a coroutine method!");
        }
        ClassElement returnType = methodElement.getGenericReturnType();
        if (returnType.getName().equals("kotlinx.coroutines.flow.Flow")) {
            return returnType.getFirstTypeArgument().orElse(null);
        }
        ParameterElement[] suspendParameters = methodElement.getSuspendParameters();
        return suspendParameters[suspendParameters.length - 1].getGenericType().getFirstTypeArgument().orElse(null);
    }

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
     * Does the given type have an {@link MappedEntity}.
     * @param type The type
     * @return True if it does
     */
    public static boolean isEntity(@Nullable ClassElement type) {
        if (type == null) {
            return false;
        }
        return type.hasAnnotation(MappedEntity.class);
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
        return type.getFirstTypeArgument().map(TypeUtils::isEntity).orElse(false);
    }

    /**
     * Does the method return an object convertible to a number.
     *
     * @param methodElement The method element
     * @return True if it does
     */
    public static boolean doesMethodProducesANumber(@NonNull MethodElement methodElement) {
        return isNumber(getMethodProducingItemType(methodElement));
    }

    /**
     * Does the method element return void.
     * @param methodElement The method element
     * @return True if it returns void
     */
    public static boolean doesReturnVoid(@NonNull MethodElement methodElement) {
        ClassElement producingItemType = getMethodProducingItemType(methodElement);
        return producingItemType == null || isVoid(producingItemType);
    }

    @Nullable
    public static ClassElement getMethodProducingItemType(@NonNull MethodElement methodElement) {
        ClassElement returnType = methodElement.getGenericReturnType();
        if (isReactiveOrFuture(returnType)) {
            returnType = returnType.getFirstTypeArgument().orElse(null);
        } else if (methodElement.isSuspend()) {
            returnType = getKotlinCoroutineProducedType(methodElement);
        }
        return returnType;
    }

    /**
     * Does the given method element return boolean.
     * @param methodElement The method element
     * @return True if it does
     */
    public static boolean doesMethodProducesABoolean(@NonNull MethodElement methodElement) {
        return isBoolean(getMethodProducingItemType(methodElement));
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
                type.isAssignable(Slice.class) ||
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
        return type != null && (type.isAssignable(Publisher.class)
                || type.getPackageName().equals("io.reactivex")
                || type.getPackageName().startsWith("kotlinx.coroutines.flow"));
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
     * Is the type a number.
     * @param type The type
     * @return True if is a number
     */
    public static boolean isComparable(@Nullable ClassElement type) {
        if (type == null) {
            return false;
        }
        return type.isAssignable(Comparable.class) || isNumber(type) || isBoolean(type);
    }

    /**
     * Is the type void.
     * @param type The type
     * @return True if is void
     */
    public static boolean isVoid(@Nullable ClassElement type) {
        return type != null && (type.isAssignable(Void.class) || type.isAssignable(void.class) || type.getName().equals("kotlin.Unit"));
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
     * Returns true if the return type is considered valid for batch update operations likes deletes and updates.
     * @param methodElement The method element
     * @return True if is valid
     */
    public static boolean isValidBatchUpdateReturnType(MethodElement methodElement) {
        return doesReturnVoid(methodElement) || doesMethodProducesANumber(methodElement);
    }

    /**
     * Checks whether the return type is supported.
     *
     * @param matchContext The match context
     * @return True if it is supported
     */
    public static boolean isValidCountReturnType(MatchContext matchContext) {
        return TypeUtils.doesMethodProducesANumber(matchContext.getMethodElement());
    }

    /**
     * Checks whether the return type is supported.
     *
     * @param methodElement The method
     * @return True if it is supported
     */
    public static boolean doesMethodProducesIterableOfAnEntity(MethodElement methodElement) {
        ClassElement returnType = methodElement.getGenericReturnType();
        if (TypeUtils.isReactiveType(returnType)) {
            return TypeUtils.isEntity(methodElement.getGenericReturnType().getFirstTypeArgument().orElse(null));
        }
        if (TypeUtils.isFutureType(returnType)) {
            returnType = returnType.getFirstTypeArgument().orElse(null);
            if (returnType != null && returnType.isAssignable(Iterable.class)) {
                // TODO:
                //  <S extends T> CompletableFuture<? extends Iterable<S>> findAll
                // getFirstTypeArgument of the Iterable is also Iterable ???
                return true;
            }
        }
        if (methodElement.isSuspend()) {
            returnType = TypeUtils.getKotlinCoroutineProducedType(methodElement);
        }
        return TypeUtils.isIterableOfEntity(returnType);
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
     * Compute the data type for the given parameter.
     * @param parameter The parameter
     * @return The data type
     */
    public static Optional<DataType> resolveDataType(@NonNull ParameterElement parameter) {
        ClassElement genericType = parameter.getGenericType();
        Objects.requireNonNull(genericType);
        if (TypeUtils.isEntityContainerType(genericType) || genericType.hasStereotype(MappedEntity.class)) {
            return Optional.of(DataType.ENTITY);
        }
        return parameter.enumValue(TypeDef.class, "type", DataType.class);
    }

    /**
     * Resolve the converter for the given type.
     * @param type The type
     * @param dataConverters Configured data converters
     * @return The data type
     */
    public static @Nullable String resolveDataConverter(@NonNull ClassElement type, Map<String, String> dataConverters) {
        Optional<String> explicitConverter = type.stringValue(TypeDef.class, "converter");
        if (explicitConverter.isPresent()) {
            return explicitConverter.get();
        }
        return dataConverters.keySet()
                .stream()
                .filter(type::isAssignable)
                .findFirst().orElse(null);
    }

    /**
     * Compute the data type for the given type.
     * @param type The type
     * @param dataTypes Configured data types
     * @return The data type
     */
    public static @NonNull DataType resolveDataType(@NonNull ClassElement type, Map<String, DataType> dataTypes) {
        final String typeName = type.isArray() ? type.getName() + "[]" : type.getName();

        return RESOLVED_DATA_TYPES.computeIfAbsent(typeName, s -> {
            if (type.isPrimitive() || typeName.startsWith("java.lang")) {
                Class primitiveType = ClassUtils.getPrimitiveType(type.getName()).orElse(null);
                if (primitiveType != null && primitiveType != void.class) {
                    String wrapperName = ReflectionUtils.getWrapperType(primitiveType).getSimpleName().toUpperCase(Locale.ENGLISH);
                    if (type.isArray())   {
                        wrapperName += "_ARRAY";
                    }
                    return DataType.valueOf(wrapperName);
                }
            }

            Optional<DataType> explicitType = type.getValue(TypeDef.class, "type", DataType.class);
            if (explicitType.isPresent()) {
                return explicitType.get();
            }

            if (type.isEnum()) {
                return DataType.STRING;
            }

            if (type.hasStereotype(MappedEntity.class)) {
                return DataType.ENTITY;
            }

            if (type.isArray()) {
                if (type.isAssignable(String.class)) {
                    return DataType.STRING_ARRAY;
                }
                if (type.isAssignable(Short.class)) {
                    return DataType.SHORT_ARRAY;
                }
                if (type.isAssignable(Integer.class)) {
                    return DataType.INTEGER_ARRAY;
                }
                if (type.isAssignable(Long.class)) {
                    return DataType.LONG_ARRAY;
                }
                if (type.isAssignable(Float.class)) {
                    return DataType.FLOAT_ARRAY;
                }
                if (type.isAssignable(Double.class)) {
                    return DataType.DOUBLE_ARRAY;
                }
                if (type.isAssignable(Character.class)) {
                    return DataType.CHARACTER_ARRAY;
                }
                if (type.isAssignable(Boolean.class)) {
                    return DataType.BOOLEAN_ARRAY;
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
            } else if (type.isAssignable(Temporal.class)) {
                if (type.isAssignable(ChronoLocalDate.class) || type.isAssignable(Year.class) || type.isAssignable(YearMonth.class)) {
                    return DataType.DATE;
                } else {
                    return DataType.TIMESTAMP;
                }
            } else if (type.isAssignable(Date.class)) {
                if (type.isAssignable(Time.class)) {
                    return DataType.TIME;
                }
                if (type.isAssignable(Timestamp.class)) {
                    return DataType.TIMESTAMP;
                } else {
                    return DataType.DATE;
                }
            } else if (type.isAssignable(UUID.class)) {
                return DataType.UUID;
            }
            if (Stream.of(Charset.class, TimeZone.class, Locale.class, URL.class, URI.class).anyMatch(type::isAssignable)) {
                return DataType.STRING;
            }

            String configured = dataTypes.keySet()
                    .stream()
                    .filter(type::isAssignable)
                    .findFirst().orElse(null);
            if (configured != null) {
                return dataTypes.get(configured);
            }
            if (ClassUtils.isJavaBasicType(type.getName())) {
                return DataType.STRING;
            } else {
                return DataType.OBJECT;
            }
        });

    }

    /**
     * Return true if the left type is compatible or can be assigned to the right type.
     * @param leftType The left type
     * @param rightType The right type
     * @return True if they are
     */
    public static boolean areTypesCompatible(ClassElement leftType, ClassElement rightType) {
        String rightTypeName = rightType.getName();
        if (leftType.getName().equals(rightTypeName)) {
            return true;
        } else if (leftType.isAssignable(rightTypeName)) {
            return true;
        } else {
            if (isNumber(leftType) && isNumber(rightType)) {
                return true;
            } else {
                return isBoolean(leftType) && isBoolean(rightType);
            }
        }
    }

    /**
     * Return the type for the given class element, wrapping primitives types if necessary.
     * @param type The type
     * @return The ID type
     */
    public static @NonNull String getTypeName(@NonNull ClassElement type) {
        String typeName = type.getName();
        return ClassUtils.getPrimitiveType(typeName).map(t ->
            ReflectionUtils.getWrapperType(t).getName()
        ).orElse(typeName);
    }
}

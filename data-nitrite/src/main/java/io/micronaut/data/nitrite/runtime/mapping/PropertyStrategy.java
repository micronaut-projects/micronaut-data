/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.nitrite.runtime.mapping;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.data.annotation.MappedEntity;

import java.util.Optional;

/**
 * Serialization strategy for a persistent property, classified once from its declared type
 * at meta-build time. The dispatch loop uses this to avoid instanceof chains and registry
 * lookups on every document conversion.
 */
public enum PropertyStrategy {
    JAVA_PASSTHROUGH,
    INSTANT,
    UUID,
    ENUM,
    LOCAL_DATE,
    LOCAL_DATETIME,
    LOCAL_TIME,
    ZONED_DATE_TIME,
    OFFSET_DATE_TIME,
    URL,
    URI,
    CHARSET,
    OPTIONAL,
    ENTITY_ID_REF,
    GEOMETRY,
    INTROSPECTED_POJO,
    SERDE,
    ASSOCIATION_MAPPED_BY,
    ASSOCIATION_EMBEDDED,
    ASSOCIATION_ID_REF,
    ASSOCIATION_IDS_REF;

    /**
     * Classify the serialization strategy for a non-association property from its declared type.
     * Called once per property during meta construction; never on the hot path.
     */
    static <T> PropertyStrategy classifyValueStrategy(Class<?> geometryClass, Class<T> type) {
      return switch (type) {
        case Class<T> _ when geometryClass != null && geometryClass.isAssignableFrom(type) -> GEOMETRY;
        case Class<T> _ when type == String.class || type == Boolean.class || type == Character.class -> JAVA_PASSTHROUGH;
        case Class<T> _ when Number.class.isAssignableFrom(type) || type.isPrimitive()    -> JAVA_PASSTHROUGH;
        case Class<T> _ when NitriteTypeRegistry.hasEntry(type)                           -> NitriteTypeRegistry.strategyFor(type);
        case Class<T> _ when type == Optional.class                                       -> OPTIONAL;
        case Class<T> _ when type.isEnum()                                                -> ENUM;
        case Class<T> _ when type.isArray() || type.getPackageName().startsWith("java.")  -> JAVA_PASSTHROUGH;
        default -> {
          Optional<BeanIntrospection<T>> intro = BeanIntrospector.SHARED.findIntrospection(type);
          yield intro.map(tBeanIntrospection -> (tBeanIntrospection.hasAnnotation(MappedEntity.class) ? ENTITY_ID_REF : INTROSPECTED_POJO)).orElse(SERDE);
        }
      };
    }
}

package io.micronaut.data.nitrite.runtime.mapping;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.data.annotation.MappedEntity;

import java.util.Map;
import java.util.Optional;

/**
 * Serialization strategy for a persistent property, classified once from its declared type
 * at meta-build time. The dispatch loop uses this to avoid instanceof chains and registry
 * lookups on every document conversion.
 */
public enum PropertyStrategy {
    /** Values are passed through directly to the underlying datastore without conversion. */
    JAVA_PASSTHROUGH,
    /** Represents an {@link java.time.Instant} value mapped to its epoch nanoseconds. */
    INSTANT,
    /** Represents a {@link java.util.UUID} mapped as a string or binary value. */
    UUID,
    /** Represents an Enum mapped as a string. */
    ENUM,
    /** Represents a {@link java.time.LocalDate} mapped as a long array. */
    LOCAL_DATE,
    /** Represents a {@link java.time.LocalDateTime} mapped as a long array. */
    LOCAL_DATETIME,
    /** Represents a {@link java.time.LocalTime} mapped as a long array. */
    LOCAL_TIME,
    /** Represents a {@link java.time.ZonedDateTime} mapped as a long array. */
    ZONED_DATE_TIME,
    /** Represents an {@link java.time.OffsetDateTime} mapped as a long array. */
    OFFSET_DATE_TIME,
    /** Represents a {@link java.net.URL} mapped as a string. */
    URL,
    /** Represents a {@link java.net.URI} mapped as a string. */
    URI,
    /** Represents a {@link java.nio.charset.Charset} mapped as a string. */
    CHARSET,
    /** Represents an {@link Optional} mapped to its inner value or null. */
    OPTIONAL,
    /** Represents a reference to another entity by its ID (foreign key). */
    ENTITY_ID_REF,
    /** Represents a geospatial Geometry object. */
    GEOMETRY,
    /** Represents a generic {@link Map}. Nested POJOs within the map are recursively hydrated correctly. */
    MAP,
    /** Represents a POJO introspected by Micronaut for serialization. */
    INTROSPECTED_POJO,
    /** Fallback serialization strategy using standard Serde conversion. */
    SERDE,
    /** Represents an association that is mapped by a property on the target entity (e.g. OneToMany). */
    ASSOCIATION_MAPPED_BY,
    /** Represents an embedded association stored as a nested document. */
    ASSOCIATION_EMBEDDED,
    /** Represents an association stored as a single ID reference. */
    ASSOCIATION_ID_REF,
    /** Represents an association stored as a collection of ID references. */
    ASSOCIATION_IDS_REF;

    /**
     * Classify the serialization strategy for a non-association property from its declared type.
     * Called once per property during meta construction; never on the hot path.
     */
    static <T> @Nullable PropertyStrategy classifyValueStrategy(@Nullable Class<?> geometryClass, Class<T> type) {
      return switch (type) {
        case Class<T> _ when geometryClass != null && geometryClass.isAssignableFrom(type) -> GEOMETRY;
        case Class<T> _ when type == String.class || type == Boolean.class || type == Character.class -> JAVA_PASSTHROUGH;
        case Class<T> _ when Number.class.isAssignableFrom(type) || type.isPrimitive()    -> JAVA_PASSTHROUGH;
        case Class<T> _ when NitriteTypeRegistry.hasEntry(type)                           -> NitriteTypeRegistry.strategyFor(type);
        case Class<T> _ when type == Map.class || Map.class.isAssignableFrom(type) -> MAP;
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

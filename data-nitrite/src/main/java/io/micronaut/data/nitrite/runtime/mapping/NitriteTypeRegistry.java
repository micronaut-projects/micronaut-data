package io.micronaut.data.nitrite.runtime.mapping;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.nitrite.runtime.ValueConverter;

import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Central registry of per-type Nitrite serialization rules.
 * Each concrete type is registered once with its {@link PropertyStrategy},
 * a write function (Java value → Nitrite storage format), and optional
 * reverse functions for reading back from a stored Number or String.
 *
 * <p>Adding support for a new type requires only a single {@link #add} call here;
 * {@link PropertyStrategy#classifyValueStrategy}, {@link ValueConverter#toFilterValueStatic},
 * {@link ValueConverter#convertWithTemporalHandling}, and the document mapper
 * all delegate to this registry automatically.</p>
 */
@Internal
public final class NitriteTypeRegistry {

    private static final Map<Class<?>, TypeEntry<?>> ENTRIES = new LinkedHashMap<>();

    static {
        add(Instant.class,        PropertyStrategy.INSTANT,
            ValueConverter::epochNanos,
            n -> ValueConverter.fromEpochNanos(n.longValue()),
            null);
        add(LocalDate.class,      PropertyStrategy.LOCAL_DATE,
            LocalDate::toEpochDay,
            n -> LocalDate.ofEpochDay(n.longValue()),
            LocalDate::parse);
        add(LocalDateTime.class,  PropertyStrategy.LOCAL_DATETIME,
            ldt -> ValueConverter.epochNanos(ldt.toInstant(ZoneOffset.UTC)),
            n -> LocalDateTime.ofInstant(ValueConverter.fromEpochNanos(n.longValue()), ZoneOffset.UTC),
            LocalDateTime::parse);
        add(LocalTime.class,      PropertyStrategy.LOCAL_TIME,
            LocalTime::toNanoOfDay,
            n -> LocalTime.ofNanoOfDay(n.longValue()),
            LocalTime::parse);
        add(ZonedDateTime.class,  PropertyStrategy.ZONED_DATE_TIME,
            zdt -> zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            null,
            ZonedDateTime::parse);
        add(OffsetDateTime.class, PropertyStrategy.OFFSET_DATE_TIME,
            odt -> odt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            null,
            OffsetDateTime::parse);
        add(UUID.class,           PropertyStrategy.UUID,
            UUID::toString,           null, null);
        add(URL.class,            PropertyStrategy.URL,
            URL::toString,            null, null);
        add(URI.class,            PropertyStrategy.URI,
            URI::toString,            null, null);
        add(Charset.class,        PropertyStrategy.CHARSET,
            Charset::name,            null, null);
        add(ZoneId.class,         PropertyStrategy.JAVA_PASSTHROUGH,
            ZoneId::getId,            null, null);
    }

    private NitriteTypeRegistry() {
    }

    private static <T> void add(Class<T> type, PropertyStrategy strategy,
                                 Function<T, Object> write,
                                 @Nullable Function<Number, T> fromNumber,
                                 @Nullable Function<String, T> fromString) {
        ENTRIES.put(type, new TypeEntry<>(strategy, write, fromNumber, fromString));
    }

    /**
     * Returns the entry for {@code type} or a registered supertype.
     *
     * @param <T> the requested Java type
     * @param type the type to resolve
     * @return the matching entry, or {@code null} if the type is not registered
     */
    public static <T> @Nullable TypeEntry<T> get(Class<T> type) {
        return findEntry(type);
    }

    /**
     * Returns whether a type entry exists for {@code type} or a registered supertype.
     *
     * @param type the type to check
     * @return {@code true} if an entry exists
     */
    public static boolean hasEntry(Class<?> type) {
        return findEntry(type) != null;
    }

    /**
     * Returns the {@link PropertyStrategy} for a registered type or supertype.
     *
     * @param type the type to resolve
     * @return the matching property strategy, or {@code null} if not registered
     */
    public static @Nullable PropertyStrategy strategyFor(Class<?> type) {
        TypeEntry<?> entry = findEntry(type);
        return entry != null ? entry.strategy() : null;
    }

    /**
     * Converts {@code value} to its Nitrite storage format using the registered write function.
     * Returns {@code value} unchanged if the type is not registered.
     *
     * <p>Walks the superclass hierarchy to handle abstract registered types (e.g. {@link Charset},
     * {@link ZoneId}) whose concrete runtime class differs from the registered key.</p>
     *
     * @param value the Java value to convert
     * @return the Nitrite storage representation, or the original value if unregistered
     */
    public static @Nullable Object write(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        TypeEntry<Object> entry = findEntry(value.getClass());
        return entry != null ? entry.write().apply(value) : value;
    }

    @SuppressWarnings("unchecked")
    private static <T> @Nullable TypeEntry<T> findEntry(Class<?> cls) {
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            TypeEntry<?> entry = ENTRIES.get(c);
            if (entry != null) {
                return (TypeEntry<T>) entry;
            }
        }
        return null;
    }

    /**
     * Converts a stored Nitrite value back to {@code targetType} using the registered reverse
     * function. Returns {@code null} if the type is not registered, or neither
     * {@code fromNumber} nor {@code fromString} applies to {@code value}.
     *
     * @param <T> the requested Java type
     * @param value the stored value
     * @param targetType the target Java type
     * @return the converted Java value, or {@code null} if no conversion applies
     */
    public static <T> @Nullable T read(@Nullable Object value, Class<T> targetType) {
        TypeEntry<T> entry = findEntry(targetType);
        if (entry == null) {
            return null;
        }
        if (value instanceof Number n && entry.fromNumber() != null) {
            return entry.fromNumber().apply(n);
        }
        if (value instanceof String s && entry.fromString() != null) {
            return entry.fromString().apply(s);
        }
        return null;
    }

    /**
     * Per-type serialization descriptor.
     *
     * @param <T> the Java type
     * @param strategy the {@link PropertyStrategy} to assign at meta-build time
     * @param write converts a Java value to its Nitrite storage format
     * @param fromNumber reverses a stored {@link Number} back to {@code T} when applicable
     * @param fromString reverses a stored ISO {@link String} back to {@code T} when applicable
     */
    public record TypeEntry<T>(
        PropertyStrategy strategy,
        Function<T, Object> write,
        @Nullable Function<Number, T> fromNumber,
        @Nullable Function<String, T> fromString
    ) {
    }
}

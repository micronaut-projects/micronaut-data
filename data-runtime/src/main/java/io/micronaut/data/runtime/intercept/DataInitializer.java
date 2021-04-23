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
package io.micronaut.data.runtime.intercept;

import io.micronaut.context.annotation.Context;
import io.micronaut.core.convert.ConversionService;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.*;
import java.time.chrono.ChronoLocalDate;
import java.util.Collection;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Internal Data initialization.
 *
 * @author graemerocher
 * @since 1.0
 */
@Context
class DataInitializer {

    /**
     * Default constructor.
     */
    DataInitializer() {
        ConversionService<?> conversionService = ConversionService.SHARED;

        conversionService.addConverter(Enum.class, Number.class, Enum::ordinal);
        conversionService.addConverter(Number.class, Enum.class, (index, targetType, context) -> {
            Enum[] enumConstants = targetType.getEnumConstants();
            int i = index.intValue();
            if (i >= enumConstants.length) {
                throw new IllegalStateException("Cannot find an enum value at index: " + i + " for enum: " + targetType);
            }
            return Optional.of(enumConstants[i]);
        });
        conversionService.addConverter(Number.class, Character.class, number -> (char) number.intValue());

        conversionService.addConverter(byte[].class, UUID.class, UUID::nameUUIDFromBytes);
        conversionService.addConverter(Date.class, LocalDate.class, date ->
            Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate());
        conversionService.addConverter(ChronoLocalDate.class, Date.class, localDate ->
            new Date(localDate.atTime(LocalTime.MIDNIGHT).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));

        // Arrays
        conversionService.addConverter(String[].class, Character[].class, values -> {
            Character[] chars = new Character[values.length];
            for (int i = 0; i < values.length; i++) {
                String value = values[i];
                chars[i] = value.length() == 0 ? Character.MIN_VALUE : value.charAt(0);
            }
            return chars;
        });
        conversionService.addConverter(String[].class, char[].class, values -> {
            char[] chars = new char[values.length];
            for (int i = 0; i < values.length; i++) {
                String value = values[i];
                chars[i] = value.length() == 0 ? Character.MIN_VALUE : value.charAt(0);
            }
            return chars;
        });
        conversionService.addConverter(Character[].class, String[].class, values -> {
            String[] strings = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                strings[i] = values[i].toString();
            }
            return strings;
        });
        conversionService.addConverter(char[].class, String[].class, values -> {
            String[] strings = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                strings[i] = String.valueOf(values[i]);
            }
            return strings;
        });
        conversionService.addConverter(Collection.class, Character[].class, collection -> {
            Character[] chars = new Character[collection.size()];
            int i = 0;
            for (Object value : collection) {
                chars[i++] = asCharacter(value);
            }
            return chars;
        });
        conversionService.addConverter(Collection.class, char[].class, collection -> {
            char[] chars = new char[collection.size()];
            int i = 0;
            for (Object value : collection) {
                chars[i++] = asCharacter(value);
            }
            return chars;
        });
        conversionService.addConverter(Character[].class, char[].class, values -> {
            char[] chars = new char[values.length];
            for (int i = 0; i < values.length; i++) {
                chars[i] = values[i];
            }
            return chars;
        });
        conversionService.addConverter(char[].class, Character[].class, values -> {
            Character[] chars = new Character[values.length];
            for (int i = 0; i < values.length; i++) {
                chars[i] = values[i];
            }
            return chars;
        });

        conversionService.addConverter(Collection.class, Short[].class, collection -> {
            Short[] shorts = new Short[collection.size()];
            int i = 0;
            for (Object value : collection) {
                shorts[i++] = asShort(value);
            }
            return shorts;
        });
        conversionService.addConverter(Collection.class, short[].class, collection -> {
            short[] shorts = new short[collection.size()];
            int i = 0;
            for (Object value : collection) {
                shorts[i++] = asShort(value);
            }
            return shorts;
        });
        conversionService.addConverter(Short[].class, short[].class, values -> {
            short[] shorts = new short[values.length];
            for (int i = 0; i < values.length; i++) {
                shorts[i] = values[i];
            }
            return shorts;
        });
        conversionService.addConverter(short[].class, Short[].class, values -> {
            Short[] shorts = new Short[values.length];
            for (int i = 0; i < values.length; i++) {
                shorts[i] = values[i];
            }
            return shorts;
        });

        conversionService.addConverter(Collection.class, Float[].class, collection -> {
            Float[] floats = new Float[collection.size()];
            int i = 0;
            for (Object value : collection) {
                floats[i++] = asFloat(value);
            }
            return floats;
        });
        conversionService.addConverter(Collection.class, float[].class, collection -> {
            float[] floats = new float[collection.size()];
            int i = 0;
            for (Object value : collection) {
                floats[i++] = asFloat(value);
            }
            return floats;
        });
        conversionService.addConverter(Float[].class, float[].class, values -> {
            float[] floats = new float[values.length];
            for (int i = 0; i < values.length; i++) {
                floats[i] = values[i];
            }
            return floats;
        });
        conversionService.addConverter(float[].class, Float[].class, values -> {
            Float[] floats = new Float[values.length];
            for (int i = 0; i < values.length; i++) {
                floats[i] = values[i];
            }
            return floats;
        });

        conversionService.addConverter(Float[].class, BigDecimal[].class, values -> {
            BigDecimal[] bigs = new BigDecimal[values.length];
            for (int i = 0; i < values.length; i++) {
                bigs[i] = BigDecimal.valueOf(values[i]);
            }
            return bigs;
        });
        conversionService.addConverter(float[].class, BigDecimal[].class, values -> {
            BigDecimal[] bigs = new BigDecimal[values.length];
            for (int i = 0; i < values.length; i++) {
                bigs[i] = BigDecimal.valueOf(values[i]);
            }
            return bigs;
        });

        conversionService.addConverter(Collection.class, Integer[].class, collection -> {
            Integer[] ints = new Integer[collection.size()];
            int i = 0;
            for (Object value : collection) {
                ints[i++] = asInteger(value);
            }
            return ints;
        });
        conversionService.addConverter(Collection.class, int[].class, collection -> {
            int[] ints = new int[collection.size()];
            int i = 0;
            for (Object value : collection) {
                ints[i++] = asInteger(value);
            }
            return ints;
        });
        conversionService.addConverter(Integer[].class, int[].class, values -> {
            int[] ints = new int[values.length];
            for (int i = 0; i < values.length; i++) {
                ints[i] = values[i];
            }
            return ints;
        });
        conversionService.addConverter(int[].class, Integer[].class, values -> {
            Integer[] ints = new Integer[values.length];
            for (int i = 0; i < values.length; i++) {
                ints[i] = values[i];
            }
            return ints;
        });

        conversionService.addConverter(Collection.class, Long[].class, collection -> {
            Long[] longs = new Long[collection.size()];
            int i = 0;
            for (Object value : collection) {
                longs[i++] = asLong(value);
            }
            return longs;
        });
        conversionService.addConverter(Collection.class, long[].class, collection -> {
            long[] longs = new long[collection.size()];
            int i = 0;
            for (Object value : collection) {
                longs[i++] = asLong(value);
            }
            return longs;
        });
        conversionService.addConverter(Long[].class, long[].class, values -> {
            long[] longs = new long[values.length];
            for (int i = 0; i < values.length; i++) {
                longs[i] = values[i];
            }
            return longs;
        });
        conversionService.addConverter(long[].class, Long[].class, values -> {
            Long[] longs = new Long[values.length];
            for (int i = 0; i < values.length; i++) {
                longs[i] = values[i];
            }
            return longs;
        });

        conversionService.addConverter(Collection.class, Double[].class, collection -> {
            Double[] doubles = new Double[collection.size()];
            int i = 0;
            for (Object value : collection) {
                doubles[i++] = asDouble(value);
            }
            return doubles;
        });
        conversionService.addConverter(Collection.class, double[].class, collection -> {
            double[] doubles = new double[collection.size()];
            int i = 0;
            for (Object value : collection) {
                doubles[i++] = asDouble(value);
            }
            return doubles;
        });
        conversionService.addConverter(Double[].class, double[].class, values -> {
            double[] doubles = new double[values.length];
            for (int i = 0; i < values.length; i++) {
                doubles[i] = values[i];
            }
            return doubles;
        });
        conversionService.addConverter(double[].class, Double[].class, values -> {
            Double[] doubles = new Double[values.length];
            for (int i = 0; i < values.length; i++) {
                doubles[i] = values[i];
            }
            return doubles;
        });
        conversionService.addConverter(Double[].class, BigDecimal[].class, values -> {
            BigDecimal[] bigs = new BigDecimal[values.length];
            for (int i = 0; i < values.length; i++) {
                bigs[i] = BigDecimal.valueOf(values[i]);
            }
            return bigs;
        });
        conversionService.addConverter(double[].class, BigDecimal[].class, values -> {
            BigDecimal[] bigs = new BigDecimal[values.length];
            for (int i = 0; i < values.length; i++) {
                bigs[i] = BigDecimal.valueOf(values[i]);
            }
            return bigs;
        });

        conversionService.addConverter(Collection.class, Boolean[].class, collection -> {
            Boolean[] booleans = new Boolean[collection.size()];
            int i = 0;
            for (Object value : collection) {
                booleans[i++] = asBoolean(value);
            }
            return booleans;
        });
        conversionService.addConverter(Collection.class, boolean[].class, collection -> {
            boolean[] booleans = new boolean[collection.size()];
            int i = 0;
            for (Object value : collection) {
                booleans[i++] = asBoolean(value);
            }
            return booleans;
        });
        conversionService.addConverter(Boolean[].class, boolean[].class, values -> {
            boolean[] booleans = new boolean[values.length];
            for (int i = 0; i < values.length; i++) {
                booleans[i] = values[i];
            }
            return booleans;
        });
        conversionService.addConverter(boolean[].class, Boolean[].class, values -> {
            Boolean[] booleans = new Boolean[values.length];
            for (int i = 0; i < values.length; i++) {
                booleans[i] = values[i];
            }
            return booleans;
        });

        // Instant
        conversionService.addConverter(Instant.class, Date.class, Date::from);
        conversionService.addConverter(Instant.class, Timestamp.class, Timestamp::from);
        conversionService.addConverter(Date.class, Instant.class, Date::toInstant);
        conversionService.addConverter(java.sql.Date.class, Instant.class, date ->
            Instant.ofEpochMilli(date.getTime()));

        // ZonedDateTime
        conversionService.addConverter(Timestamp.class, ZonedDateTime.class, timestamp ->
            timestamp.toLocalDateTime().atZone(ZoneId.systemDefault()));
        conversionService.addConverter(ZonedDateTime.class, Timestamp.class, zonedDateTime ->
            Timestamp.from(zonedDateTime.toInstant()));

        // LocalDateTime
        conversionService.addConverter(LocalDateTime.class, Date.class, localDateTime ->
            new Date(localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()));
        conversionService.addConverter(LocalDateTime.class, Timestamp.class, Timestamp::valueOf);
        conversionService.addConverter(Timestamp.class, LocalDateTime.class, Timestamp::toLocalDateTime);
        conversionService.addConverter(Date.class, LocalDateTime.class, date ->
            Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime());

        // OffsetDateTime
        conversionService.addConverter(Date.class, OffsetDateTime.class, date ->
            Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toOffsetDateTime());
        conversionService.addConverter(OffsetDateTime.class, Date.class, offsetDateTime2 ->
            new Date(offsetDateTime2.toInstant().toEpochMilli()));
        conversionService.addConverter(OffsetDateTime.class, java.sql.Date.class, offsetDateTime1 ->
            new java.sql.Date(offsetDateTime1.toInstant().toEpochMilli()));
        conversionService.addConverter(OffsetDateTime.class, Timestamp.class, offsetDateTime1 ->
            Timestamp.from(offsetDateTime1.toInstant()));
        conversionService.addConverter(Timestamp.class, OffsetDateTime.class, timestamp ->
            OffsetDateTime.ofInstant(timestamp.toInstant(), ZoneId.systemDefault()));
        conversionService.addConverter(OffsetDateTime.class, Instant.class, OffsetDateTime::toInstant);
        conversionService.addConverter(OffsetDateTime.class, LocalDate.class, OffsetDateTime::toLocalDate);
        conversionService.addConverter(OffsetDateTime.class, LocalDateTime.class, OffsetDateTime::toLocalDateTime);
        conversionService.addConverter(OffsetDateTime.class, Long.class, offsetDateTime ->
            offsetDateTime.toInstant().toEpochMilli());
    }

    private Integer asInteger(Object value) {
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return ConversionService.SHARED.convertRequired(value, Integer.class);
    }

    private Long asLong(Object value) {
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return ConversionService.SHARED.convertRequired(value, Long.class);
    }

    private Double asDouble(Object value) {
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return ConversionService.SHARED.convertRequired(value, Double.class);
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return ConversionService.SHARED.convertRequired(value, Boolean.class);
    }

    private Float asFloat(Object value) {
        if (value instanceof Float) {
            return (Float) value;
        }
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return ConversionService.SHARED.convertRequired(value, Float.class);
    }

    private Short asShort(Object value) {
        if (value instanceof Short) {
            return (Short) value;
        }
        if (value instanceof Number) {
            return ((Number) value).shortValue();
        }
        return ConversionService.SHARED.convertRequired(value, Short.class);
    }

    private Character asCharacter(Object value) {
        if (value instanceof Character) {
            return (Character) value;
        }
        return ConversionService.SHARED.convertRequired(value, Character.class);
    }
}

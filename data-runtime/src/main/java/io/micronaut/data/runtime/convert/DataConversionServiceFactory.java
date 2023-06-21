/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.runtime.convert;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.type.Argument;
import io.micronaut.data.exceptions.DataAccessException;
import jakarta.inject.Singleton;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoLocalDate;
import java.time.chrono.ChronoZonedDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * The {@link DataConversionService} factory class.
 *
 * @author Denis Stepanov
 * @since 3.1
 */
@Internal
@Factory
final class DataConversionServiceFactory {

    private static final String SQL_ARRAY_READ_ERROR = "Unable to read SQL array";

    @Singleton
    @Bean(typed = DataConversionService.class)
    DataConversionServiceImpl build(@NonNull BeanContext beanContext) {
        DataConversionServiceImpl conversionService = new DataConversionServiceImpl(beanContext.getConversionService());
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
                chars[i++] = asCharacter(value, conversionService);
            }
            return chars;
        });
        conversionService.addConverter(Collection.class, char[].class, collection -> {
            char[] chars = new char[collection.size()];
            int i = 0;
            for (Object value : collection) {
                chars[i++] = asCharacter(value, conversionService);
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
                shorts[i++] = asShort(value, conversionService);
            }
            return shorts;
        });
        conversionService.addConverter(Collection.class, short[].class, collection -> {
            short[] shorts = new short[collection.size()];
            int i = 0;
            for (Object value : collection) {
                shorts[i++] = asShort(value, conversionService);
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
                floats[i++] = asFloat(value, conversionService);
            }
            return floats;
        });
        conversionService.addConverter(Collection.class, float[].class, collection -> {
            float[] floats = new float[collection.size()];
            int i = 0;
            for (Object value : collection) {
                floats[i++] = asFloat(value, conversionService);
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
                ints[i++] = asInteger(value, conversionService);
            }
            return ints;
        });
        conversionService.addConverter(Collection.class, int[].class, collection -> {
            int[] ints = new int[collection.size()];
            int i = 0;
            for (Object value : collection) {
                ints[i++] = asInteger(value, conversionService);
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
                longs[i++] = asLong(value, conversionService);
            }
            return longs;
        });
        conversionService.addConverter(Collection.class, long[].class, collection -> {
            long[] longs = new long[collection.size()];
            int i = 0;
            for (Object value : collection) {
                longs[i++] = asLong(value, conversionService);
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
                doubles[i++] = asDouble(value, conversionService);
            }
            return doubles;
        });
        conversionService.addConverter(Collection.class, double[].class, collection -> {
            double[] doubles = new double[collection.size()];
            int i = 0;
            for (Object value : collection) {
                doubles[i++] = asDouble(value, conversionService);
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
                booleans[i++] = asBoolean(value, conversionService);
            }
            return booleans;
        });
        conversionService.addConverter(Collection.class, boolean[].class, collection -> {
            boolean[] booleans = new boolean[collection.size()];
            int i = 0;
            for (Object value : collection) {
                booleans[i++] = asBoolean(value, conversionService);
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
        // java.sql.Array arrays (needed for Postgres PgArray)
        conversionService.addConverter(java.sql.Array.class, String[].class, sqlArray -> {
            Object[] array;
            try {
                array = (Object[]) sqlArray.getArray();
            } catch (SQLException e) {
                throw new DataAccessException(SQL_ARRAY_READ_ERROR, e);
            }
            String[] strings = new String[array.length];
            int i = 0;
            for (Object value : array) {
                strings[i++] = value != null ? value.toString() : null;
            }
            return strings;
        });
        conversionService.addConverter(java.sql.Array.class, Integer[].class, sqlArray -> {
            Object[] array;
            try {
                array = (Object[]) sqlArray.getArray();
            } catch (SQLException e) {
                throw new DataAccessException(SQL_ARRAY_READ_ERROR, e);
            }
            Integer[] integers = new Integer[array.length];
            int i = 0;
            for (Object value : array) {
                integers[i++] = asInteger(value, conversionService);
            }
            return integers;
        });
        conversionService.addConverter(java.sql.Array.class, int[].class, sqlArray -> {
            Object[] array;
            try {
                array = (Object[]) sqlArray.getArray();
            } catch (SQLException e) {
                throw new DataAccessException(SQL_ARRAY_READ_ERROR, e);
            }
            int[] ints = new int[array.length];
            int i = 0;
            for (Object value : array) {
                ints[i++] = asInteger(value, conversionService);
            }
            return ints;
        });
        conversionService.addConverter(java.sql.Array.class, Short[].class, sqlArray -> {
            Object[] array;
            try {
                array = (Object[]) sqlArray.getArray();
            } catch (SQLException e) {
                throw new DataAccessException(SQL_ARRAY_READ_ERROR, e);
            }
            Short[] shorts = new Short[array.length];
            int i = 0;
            for (Object value : array) {
                shorts[i++] = asShort(value, conversionService);
            }
            return shorts;
        });
        conversionService.addConverter(java.sql.Array.class, short[].class, sqlArray -> {
            Object[] array;
            try {
                array = (Object[]) sqlArray.getArray();
            } catch (SQLException e) {
                throw new DataAccessException(SQL_ARRAY_READ_ERROR, e);
            }
            short[] shorts = new short[array.length];
            int i = 0;
            for (Object value : array) {
                shorts[i++] = asShort(value, conversionService);
            }
            return shorts;
        });
        conversionService.addConverter(java.sql.Array.class, Long[].class, sqlArray -> {
            Object[] array;
            try {
                array = (Object[]) sqlArray.getArray();
            } catch (SQLException e) {
                throw new DataAccessException(SQL_ARRAY_READ_ERROR, e);
            }
            Long[] longs = new Long[array.length];
            int i = 0;
            for (Object value : array) {
                longs[i++] = asLong(value, conversionService);
            }
            return longs;
        });
        conversionService.addConverter(java.sql.Array.class, long[].class, sqlArray -> {
            Object[] array;
            try {
                array = (Object[]) sqlArray.getArray();
            } catch (SQLException e) {
                throw new DataAccessException(SQL_ARRAY_READ_ERROR, e);
            }
            long[] longs = new long[array.length];
            int i = 0;
            for (Object value : array) {
                longs[i++] = asLong(value, conversionService);
            }
            return longs;
        });
        conversionService.addConverter(java.sql.Array.class, Float[].class, sqlArray -> {
            Object[] array;
            try {
                array = (Object[]) sqlArray.getArray();
            } catch (SQLException e) {
                throw new DataAccessException(SQL_ARRAY_READ_ERROR, e);
            }
            Float[] floats = new Float[array.length];
            int i = 0;
            for (Object value : array) {
                floats[i++] = asFloat(value, conversionService);
            }
            return floats;
        });
        conversionService.addConverter(java.sql.Array.class, float[].class, sqlArray -> {
            Object[] array;
            try {
                array = (Object[]) sqlArray.getArray();
            } catch (SQLException e) {
                throw new DataAccessException(SQL_ARRAY_READ_ERROR, e);
            }
            float[] floats = new float[array.length];
            int i = 0;
            for (Object value : array) {
                floats[i++] = asFloat(value, conversionService);
            }
            return floats;
        });
        conversionService.addConverter(java.sql.Array.class, Double[].class, sqlArray -> {
            Object[] array;
            try {
                array = (Object[]) sqlArray.getArray();
            } catch (SQLException e) {
                throw new DataAccessException(SQL_ARRAY_READ_ERROR, e);
            }
            Double[] doubles = new Double[array.length];
            int i = 0;
            for (Object value : array) {
                doubles[i++] = asDouble(value, conversionService);
            }
            return doubles;
        });
        conversionService.addConverter(java.sql.Array.class, double[].class, sqlArray -> {
            Object[] array;
            try {
                array = (Object[]) sqlArray.getArray();
            } catch (SQLException e) {
                throw new DataAccessException(SQL_ARRAY_READ_ERROR, e);
            }
            double[] doubles = new double[array.length];
            int i = 0;
            for (Object value : array) {
                doubles[i++] = asDouble(value, conversionService);
            }
            return doubles;
        });
        conversionService.addConverter(java.sql.Array.class, Boolean[].class, sqlArray -> {
            Object[] array;
            try {
                array = (Object[]) sqlArray.getArray();
            } catch (SQLException e) {
                throw new DataAccessException(SQL_ARRAY_READ_ERROR, e);
            }
            Boolean[] booleans = new Boolean[array.length];
            int i = 0;
            for (Object value : array) {
                booleans[i++] = asBoolean(value, conversionService);
            }
            return booleans;
        });
        conversionService.addConverter(java.sql.Array.class, boolean[].class, sqlArray -> {
            Object[] array;
            try {
                array = (Object[]) sqlArray.getArray();
            } catch (SQLException e) {
                throw new DataAccessException(SQL_ARRAY_READ_ERROR, e);
            }
            boolean[] booleans = new boolean[array.length];
            int i = 0;
            for (Object value : array) {
                booleans[i++] = asBoolean(value, conversionService);
            }
            return booleans;
        });
        conversionService.addConverter(java.sql.Array.class, Character[].class, sqlArray -> {
            Object[] array;
            try {
                array = (Object[]) sqlArray.getArray();
            } catch (SQLException e) {
                throw new DataAccessException(SQL_ARRAY_READ_ERROR, e);
            }
            Character[] characters = new Character[array.length];
            int i = 0;
            for (Object value : array) {
                characters[i++] = asCharacter(value, conversionService);
            }
            return characters;
        });
        conversionService.addConverter(java.sql.Array.class, char[].class, sqlArray -> {
            Object[] array;
            try {
                array = (Object[]) sqlArray.getArray();
            } catch (SQLException e) {
                throw new DataAccessException(SQL_ARRAY_READ_ERROR, e);
            }
            char[] chars = new char[array.length];
            int i = 0;
            for (Object value : array) {
                chars[i++] = asCharacter(value, conversionService);
            }
            return chars;
        });
        // Conversions needed for r2dbc since arrays are returned as Object[] and not converted to expected types
        conversionService.addConverter(Object[].class, short[].class, array -> {
           short[] shorts = new short[array.length];
            int i = 0;
            for (Object value : array) {
                shorts[i++] = asShort(value, conversionService);
            }
            return shorts;
        });
        conversionService.addConverter(Object[].class, int[].class, array -> {
            int[] ints = new int[array.length];
            int i = 0;
            for (Object value : array) {
                ints[i++] = asInteger(value, conversionService);
            }
            return ints;
        });
        conversionService.addConverter(Object[].class, long[].class, array -> {
            long[] longs = new long[array.length];
            int i = 0;
            for (Object value : array) {
                longs[i++] = asLong(value, conversionService);
            }
            return longs;
        });
        conversionService.addConverter(Object[].class, float[].class, array -> {
            float[] floats = new float[array.length];
            int i = 0;
            for (Object value : array) {
                floats[i++] = asFloat(value, conversionService);
            }
            return floats;
        });
        conversionService.addConverter(Object[].class, double[].class, array -> {
            double[] doubles = new double[array.length];
            int i = 0;
            for (Object value : array) {
                doubles[i++] = asDouble(value, conversionService);
            }
            return doubles;
        });
        conversionService.addConverter(Object[].class, boolean[].class, array -> {
            boolean[] booleans = new boolean[array.length];
            int i = 0;
            for (Object value : array) {
                booleans[i++] = asBoolean(value, conversionService);
            }
            return booleans;
        });
        conversionService.addConverter(Object[].class, char[].class, array -> {
            char[] chars = new char[array.length];
            int i = 0;
            for (Object value : array) {
                chars[i++] = asCharacter(value, conversionService);
            }
            return chars;
        });

        // Instant
        Function<Instant, ZonedDateTime> instantToZonedDateTime = instant -> instant.atZone(ZoneId.systemDefault());
        addZonedConvertorsConvertors(conversionService, Instant.class, instantToZonedDateTime);

        // ZonedDateTime
        addZonedConvertorsConvertors(conversionService, ZonedDateTime.class, Function.identity());

        // LocalTime
        conversionService.addConverter(LocalTime.class, Timestamp.class, localTime -> Timestamp.valueOf(localTime.atDate(LocalDate.now())));
        conversionService.addConverter(LocalTime.class, Instant.class, localTime -> localTime.atDate(LocalDate.now()).atZone(ZoneId.systemDefault()).toInstant());
        conversionService.addConverter(LocalTime.class, Time.class, Time::valueOf);

        // LocalDateTime
        conversionService.addConverter(LocalDateTime.class, Date.class, localDateTime -> Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant()));
        conversionService.addConverter(LocalDateTime.class, Instant.class, localDateTime -> localDateTime.atZone(ZoneId.systemDefault()).toInstant());
        conversionService.addConverter(LocalDateTime.class, Timestamp.class, Timestamp::valueOf);
        conversionService.addConverter(LocalDateTime.class, LocalDate.class, LocalDateTime::toLocalDate);
        conversionService.addConverter(LocalDateTime.class, LocalTime.class, LocalDateTime::toLocalTime);
        conversionService.addConverter(LocalDateTime.class, ZonedDateTime.class, localDateTime -> localDateTime.atZone(ZoneId.systemDefault()));
        conversionService.addConverter(LocalDateTime.class, Time.class, localDateTime -> Time.valueOf(localDateTime.toLocalTime()));

        // OffsetDateTime
        conversionService.addConverter(OffsetDateTime.class, Date.class, offsetDateTime -> Date.from(offsetDateTime.toInstant()));
        conversionService.addConverter(OffsetDateTime.class, java.sql.Date.class, offsetDateTime -> new java.sql.Date(offsetDateTime.toInstant().toEpochMilli()));
        conversionService.addConverter(OffsetDateTime.class, Timestamp.class, offsetDateTime -> Timestamp.from(offsetDateTime.toInstant()));
        conversionService.addConverter(OffsetDateTime.class, Instant.class, OffsetDateTime::toInstant);
        conversionService.addConverter(OffsetDateTime.class, LocalDate.class, OffsetDateTime::toLocalDate);
        conversionService.addConverter(OffsetDateTime.class, LocalDateTime.class, OffsetDateTime::toLocalDateTime);
        conversionService.addConverter(OffsetDateTime.class, ZonedDateTime.class, OffsetDateTime::toZonedDateTime);
        conversionService.addConverter(OffsetDateTime.class, Long.class, offsetDateTime -> offsetDateTime.toInstant().toEpochMilli());

        // Date
        addZonedConvertorsConvertors(conversionService, Date.class, date -> instantToZonedDateTime.apply(Instant.ofEpochMilli(date.getTime())));

        // SQL Date
        addZonedConvertorsConvertors(conversionService, java.sql.Date.class, date -> instantToZonedDateTime.apply(Instant.ofEpochMilli(date.getTime())));

        // Timestamp
        addZonedConvertorsConvertors(conversionService, Timestamp.class, timestamp -> instantToZonedDateTime.apply(timestamp.toInstant()));

        if (beanContext != null) {
            Collection<BeanRegistration<DataTypeConverter>> typeConverters = beanContext.getBeanRegistrations(DataTypeConverter.class);
            for (BeanRegistration<DataTypeConverter> typeConverterRegistration : typeConverters) {
                TypeConverter typeConverter = typeConverterRegistration.getBean();
                List<Argument<?>> typeArguments = typeConverterRegistration.getBeanDefinition().getTypeArguments(TypeConverter.class);
                if (typeArguments.size() == 2) {
                    Class source = typeArguments.get(0).getType();
                    Class target = typeArguments.get(1).getType();
                    if (source != null && target != null && !(source == Object.class && target == Object.class)) {
                        conversionService.addConverter(source, target, typeConverter);
                    }
                }
            }
            Collection<TypeConverterRegistrar> registrars = beanContext.getBeansOfType(TypeConverterRegistrar.class);
            MutableConversionService mutableConversionService = conversionService.getMutableConversionService();
            for (TypeConverterRegistrar registrar : registrars) {
                registrar.register(mutableConversionService);
            }
        }

        return conversionService;
    }

    private <T> void addZonedConvertorsConvertors(DataConversionServiceImpl conversionService, Class<T> dateType, Function<T, ZonedDateTime> dateToZonedDateTime) {
        conversionService.addConverter(dateType, ZonedDateTime.class, dateToZonedDateTime);
        conversionService.addConverter(dateType, OffsetDateTime.class, dateToZonedDateTime.andThen(ZonedDateTime::toOffsetDateTime));
        conversionService.addConverter(dateType, LocalDateTime.class, dateToZonedDateTime.andThen(ZonedDateTime::toLocalDateTime));
        conversionService.addConverter(dateType, LocalDate.class, dateToZonedDateTime.andThen(ZonedDateTime::toLocalDate));
        conversionService.addConverter(dateType, LocalTime.class, dateToZonedDateTime.andThen(ZonedDateTime::toLocalTime));
        conversionService.addConverter(dateType, Instant.class, dateToZonedDateTime.andThen(ChronoZonedDateTime::toInstant));
        conversionService.addConverter(dateType, Date.class, dateToZonedDateTime.andThen(zonedDateTime -> Date.from(zonedDateTime.toInstant())));
        conversionService.addConverter(dateType, Timestamp.class, dateToZonedDateTime.andThen(zonedDateTime -> Timestamp.from(zonedDateTime.toInstant())));
    }

    private Integer asInteger(Object value, DataConversionService dataConversionService) {
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return dataConversionService.convertRequired(value, Integer.class);
    }

    private Long asLong(Object value, DataConversionService dataConversionService) {
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return dataConversionService.convertRequired(value, Long.class);
    }

    private Double asDouble(Object value, DataConversionService dataConversionService) {
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return dataConversionService.convertRequired(value, Double.class);
    }

    private Boolean asBoolean(Object value, DataConversionService dataConversionService) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return dataConversionService.convertRequired(value, Boolean.class);
    }

    private Float asFloat(Object value, DataConversionService dataConversionService) {
        if (value instanceof Float) {
            return (Float) value;
        }
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return dataConversionService.convertRequired(value, Float.class);
    }

    private Short asShort(Object value, DataConversionService dataConversionService) {
        if (value instanceof Short) {
            return (Short) value;
        }
        if (value instanceof Number) {
            return ((Number) value).shortValue();
        }
        return dataConversionService.convertRequired(value, Short.class);
    }

    private Character asCharacter(Object value, DataConversionService dataConversionService) {
        if (value instanceof Character) {
            return (Character) value;
        }
        return dataConversionService.convertRequired(value, Character.class);
    }
}

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
package io.micronaut.data.mongodb.geo;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.annotation.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts {@link MongoGeoPoint} to and from a GeoJSON-like persisted map.
 *
 * @author radovanradic
 * @since 5.0.0
 */
@Singleton
public final class MongoGeoPointConverter implements AttributeConverter<Object, Map<String, Object>> {

    private static final String[] LONGITUDE_NAMES = {"x", "longitude", "lng", "lon"};
    private static final String[] LATITUDE_NAMES = {"y", "latitude", "lat"};

    @Override
    public @Nullable Map<String, Object> convertToPersistedValue(@Nullable Object entityValue,
                                                                  @NonNull ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        MongoGeoPoint point;
        if (entityValue instanceof MongoGeoPointLike pointLike) {
            point = new MongoGeoPoint(pointLike.x(), pointLike.y());
        } else {
            point = toPoint(entityValue);
        }
        Map<String, Object> geoJsonPoint = new LinkedHashMap<>();
        geoJsonPoint.put("type", "Point");
        geoJsonPoint.put("coordinates", List.of(point.x(), point.y()));
        return geoJsonPoint;
    }

    @Override
    public @Nullable Object convertToEntityValue(@Nullable Map<String, Object> persistedValue,
                                                 @NonNull ConversionContext context) {
        if (persistedValue == null) {
            return null;
        }
        MongoGeoPoint point = toPoint(persistedValue);
        Class<?> targetType = MongoGeoPoint.class;
        if (context instanceof ArgumentConversionContext<?> argumentConversionContext) {
            targetType = argumentConversionContext.getArgument().getType();
        }
        if (targetType == Object.class
                || targetType == MongoGeoPoint.class
                || targetType == MongoGeoPointLike.class
                || targetType.isAssignableFrom(MongoGeoPoint.class)) {
            return point;
        }
        return toTargetType(point, targetType);
    }

    private MongoGeoPoint toPoint(Object value) {
        if (value instanceof MongoGeoPointLike pointLike) {
            return new MongoGeoPoint(pointLike.x(), pointLike.y());
        }
        if (value instanceof Map<?, ?> map) {
            return pointFromMap(map);
        }
        BeanIntrospection<?> introspection = BeanIntrospection.getIntrospection(value.getClass());
        double longitude = readCoordinate(introspection, value, LONGITUDE_NAMES);
        double latitude = readCoordinate(introspection, value, LATITUDE_NAMES);
        return new MongoGeoPoint(longitude, latitude);
    }

    private MongoGeoPoint pointFromMap(Map<?, ?> persistedValue) {
        Object type = persistedValue.get("type");
        if (type != null && !"Point".equals(type)) {
            throw new IllegalArgumentException("Invalid persisted MongoGeoPoint type: " + type);
        }
        Object coordinates = persistedValue.get("coordinates");
        if (coordinates instanceof List<?> list && list.size() == 2) {
            Object x = list.get(0);
            Object y = list.get(1);
            if (x instanceof Number xNumber && y instanceof Number yNumber) {
                return new MongoGeoPoint(xNumber.doubleValue(), yNumber.doubleValue());
            }
        }
        throw new IllegalArgumentException("Invalid persisted MongoGeoPoint value: " + persistedValue);
    }

    private double readCoordinate(BeanIntrospection<?> introspection, Object value, String[] coordinateNames) {
        for (String coordinateName : coordinateNames) {
            @SuppressWarnings("unchecked")
            BeanProperty<Object, Object> beanProperty = (BeanProperty<Object, Object>) introspection.getProperty(coordinateName).orElse(null);
            if (beanProperty == null) {
                continue;
            }
            Object coordinateValue = beanProperty.get(value);
            if (coordinateValue instanceof Number number) {
                return number.doubleValue();
            }
        }
        throw new IllegalArgumentException("Cannot extract Mongo geospatial coordinates from type ["
                + introspection.getBeanType().getName()
                + "]; expected numeric properties named one of "
                + List.of(coordinateNames));
    }

    private Object toTargetType(MongoGeoPoint point, Class<?> targetType) {
        BeanIntrospection<?> introspection = BeanIntrospection.getIntrospection(targetType);
        Object instance;
        try {
            instance = introspection.instantiate();
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot instantiate Mongo geospatial type ["
                    + targetType.getName()
                    + "] from coordinates. Provide a no-args constructor or use MongoGeoPointLike.", e);
        }
        writeCoordinate(introspection, instance, point.x(), LONGITUDE_NAMES);
        writeCoordinate(introspection, instance, point.y(), LATITUDE_NAMES);
        return instance;
    }

    private void writeCoordinate(BeanIntrospection<?> introspection,
                                 Object instance,
                                 double value,
                                 String[] coordinateNames) {
        for (String coordinateName : coordinateNames) {
            @SuppressWarnings("unchecked")
            BeanProperty<Object, Object> beanProperty = (BeanProperty<Object, Object>) introspection.getProperty(coordinateName).orElse(null);
            if (beanProperty == null || beanProperty.isReadOnly()) {
                continue;
            }
            Class<?> propertyType = beanProperty.getType();
            if (propertyType == double.class || propertyType == Double.class) {
                beanProperty.set(instance, value);
                return;
            }
            if (propertyType == float.class || propertyType == Float.class) {
                beanProperty.set(instance, (float) value);
                return;
            }
            if (propertyType == int.class || propertyType == Integer.class) {
                beanProperty.set(instance, (int) value);
                return;
            }
            if (propertyType == long.class || propertyType == Long.class) {
                beanProperty.set(instance, (long) value);
                return;
            }
        }
        throw new IllegalArgumentException("Cannot write Mongo geospatial coordinate into type ["
                + introspection.getBeanType().getName()
                + "]; expected writable numeric property named one of "
                + List.of(coordinateNames));
    }

    /**
     * @param type The property type
     * @return Whether this type should use implicit point conversion
     */
    public static boolean supportsImplicitPointType(Class<?> type) {
        if (type == MongoGeoPoint.class
                || type == MongoGeoPointLike.class
                || MongoGeoPointLike.class.isAssignableFrom(type)
                || type.isAssignableFrom(MongoGeoPoint.class)) {
            return true;
        }
        return hasPointLikeBeanShape(type);
    }

    private static boolean hasPointLikeBeanShape(Class<?> type) {
        BeanIntrospection<?> introspection;
        try {
            introspection = BeanIntrospection.getIntrospection(type);
        } catch (Exception e) {
            return false;
        }
        return hasNumericCoordinateProperty(introspection, LONGITUDE_NAMES)
                && hasNumericCoordinateProperty(introspection, LATITUDE_NAMES);
    }

    private static boolean hasNumericCoordinateProperty(BeanIntrospection<?> introspection, String[] coordinateNames) {
        for (String coordinateName : coordinateNames) {
            @SuppressWarnings("unchecked")
            BeanProperty<Object, Object> beanProperty = (BeanProperty<Object, Object>) introspection.getProperty(coordinateName).orElse(null);
            if (beanProperty == null || beanProperty.isReadOnly()) {
                continue;
            }
            Class<?> propertyType = beanProperty.getType();
            if (propertyType == double.class || propertyType == Double.class
                    || propertyType == float.class || propertyType == Float.class
                    || propertyType == int.class || propertyType == Integer.class
                    || propertyType == long.class || propertyType == Long.class) {
                return true;
            }
        }
        return false;
    }
}

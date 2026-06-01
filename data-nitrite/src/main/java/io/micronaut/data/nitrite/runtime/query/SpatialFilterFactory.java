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
package io.micronaut.data.nitrite.runtime.query;

// TODO: move to query/spatial/ subpackage once the optional-dependency interface is defined

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import org.dizitart.no2.filters.Filter;

import java.util.Map;

@Internal
final class SpatialFilterFactory {

    private static final String SPATIAL_FLUENT_FILTER_CLASS = "org.dizitart.no2.spatial.SpatialFluentFilter";
    private static final String GEO_POINT_CLASS             = "org.dizitart.no2.spatial.GeoPoint";
    private static final String JTS_GEOMETRY_CLASS          = "org.locationtech.jts.geom.Geometry";
    private static final String JTS_POINT_CLASS             = "org.locationtech.jts.geom.Point";
    private static final String JTS_COORDINATE_CLASS        = "org.locationtech.jts.geom.Coordinate";

    private final NitriteEntityMapper entityMapper;
    private final ValueResolver valueResolver;

    SpatialFilterFactory(NitriteEntityMapper entityMapper, ValueResolver valueResolver) {
        this.entityMapper = entityMapper;
        this.valueResolver = valueResolver;
    }

    Filter buildNearFilter(String field, Object value, Object[] params, Map<String, Object> namedParameters) {
        if (value instanceof Map<?, ?> m) {
            Object center = entityMapper.toNitriteFilterValue(
                valueResolver.preConvertForFilter(valueResolver.resolveValue(m.get("center"), params, namedParameters)));
            Object distanceObj = valueResolver.resolveValue(m.get("distance"), params, namedParameters);
            double distance = distanceObj instanceof Number n ? n.doubleValue() : 0.0;

            if (ClassUtils.isPresent(SPATIAL_FLUENT_FILTER_CLASS, null)) {
                try {
                    Class<?> spatialClass = Class.forName(SPATIAL_FLUENT_FILTER_CLASS);
                    Object sff = spatialClass.getMethod("where", String.class).invoke(null, field);

                    if (center != null && ClassUtils.isPresent(GEO_POINT_CLASS, null)) {
                        Class<?> geoPointClass = Class.forName(GEO_POINT_CLASS);
                        if (geoPointClass.isInstance(center)) {
                            Object point = geoPointClass.getMethod("getPoint").invoke(center);
                            return (Filter) sff.getClass().getMethod("near", Class.forName(JTS_POINT_CLASS), Double.class).invoke(sff, point, distance);
                        }
                    }
                    if (center != null && ClassUtils.isPresent(JTS_POINT_CLASS, null)) {
                        Class<?> pointClass = Class.forName(JTS_POINT_CLASS);
                        if (pointClass.isInstance(center)) {
                            return (Filter) sff.getClass().getMethod("near", pointClass, Double.class).invoke(sff, center, distance);
                        }
                    }
                    if (center != null && ClassUtils.isPresent(JTS_GEOMETRY_CLASS, null)) {
                        Class<?> geometryClass = Class.forName(JTS_GEOMETRY_CLASS);
                        if (geometryClass.isInstance(center)) {
                            Object coord = geometryClass.getMethod("getCoordinate").invoke(center);
                            if (coord != null) {
                                return (Filter) sff.getClass().getMethod("near", Class.forName(JTS_COORDINATE_CLASS), Double.class).invoke(sff, coord, distance);
                            }
                        }
                    }
                    if (center != null && ClassUtils.isPresent(JTS_COORDINATE_CLASS, null)) {
                        Class<?> coordClass = Class.forName(JTS_COORDINATE_CLASS);
                        if (coordClass.isInstance(center)) {
                            return (Filter) sff.getClass().getMethod("near", coordClass, Double.class).invoke(sff, center, distance);
                        }
                    }
                    if (center != null) {
                        throw new DataAccessException("Unsupported center type for $near spatial filter: " + center.getClass().getName());
                    }
                } catch (DataAccessException e) {
                    throw e;
                } catch (Exception e) {
                    throw new DataAccessException("Failed to create $near spatial filter on field '" + field + "': " + e.getMessage(), e);
                }
            }
        }
        return Filter.ALL;
    }

    Filter createSpatialFilter(String field, Object geometry, String method) {
        if (geometry == null) return Filter.ALL;
        if (ClassUtils.isPresent(SPATIAL_FLUENT_FILTER_CLASS, null)) {
            try {
                Class<?> spatialClass = Class.forName(SPATIAL_FLUENT_FILTER_CLASS);
                Object sff = spatialClass.getMethod("where", String.class).invoke(null, field);

                if (ClassUtils.isPresent(GEO_POINT_CLASS, null)) {
                    Class<?> geoPointClass = Class.forName(GEO_POINT_CLASS);
                    if (geoPointClass.isInstance(geometry)) {
                        Object point = geoPointClass.getMethod("getPoint").invoke(geometry);
                        return (Filter) sff.getClass().getMethod(method, Class.forName(JTS_POINT_CLASS)).invoke(sff, point);
                    }
                }
                if (ClassUtils.isPresent(JTS_GEOMETRY_CLASS, null)) {
                    Class<?> geometryClass = Class.forName(JTS_GEOMETRY_CLASS);
                    if (geometryClass.isInstance(geometry)) {
                        return (Filter) sff.getClass().getMethod(method, geometryClass).invoke(sff, geometry);
                    }
                }
                throw new DataAccessException("Unsupported geometry type for $" + method + " spatial filter: " + geometry.getClass().getName());
            } catch (DataAccessException e) {
                throw e;
            } catch (Exception e) {
                throw new DataAccessException("Failed to create $" + method + " spatial filter on field '" + field + "': " + e.getMessage(), e);
            }
        }
        return Filter.ALL;
    }
}

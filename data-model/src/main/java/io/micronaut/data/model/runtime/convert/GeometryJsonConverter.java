package io.micronaut.data.model.runtime.convert;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.serialize.exceptions.SerializationException;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.model.geo.Geometry;
import io.micronaut.data.model.geo.GeometryCollection;
import io.micronaut.data.model.geo.LineString;
import io.micronaut.data.model.geo.MultiLineString;
import io.micronaut.data.model.geo.MultiPoint;
import io.micronaut.data.model.geo.MultiPolygon;
import io.micronaut.data.model.geo.Point;
import io.micronaut.data.model.geo.Polygon;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts {@link Geometry} values to and from their GeoJSON string representation.
 *
 * <p>This converter is used for geo model types that are persisted as JSON text and supports
 * all Micronaut Data geometry implementations, including nested {@link GeometryCollection}
 * values.
 *
 * @since 5.0
 */
@Singleton
public final class GeometryJsonConverter implements AttributeConverter<Geometry, String> {

    private final JsonMapper jsonMapper;

    GeometryJsonConverter(JsonMapper jsonMapper, @Nullable @Named("oracleJdbcJsonText") ObjectMapper oracleJsonMapper) {
        this.jsonMapper = oracleJsonMapper == null ? jsonMapper : oracleJsonMapper;
    }

    @Override
    @Nullable
    public String convertToPersistedValue(@Nullable Geometry entityValue, ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        GeoJson geoJson = getGeoJson(entityValue);
        try {
            return jsonMapper.writeValueAsString(geoJson);
        } catch (IOException e) {
            throw new SerializationException("Failed to serialize GeoJson entity [" + geoJson + "]", e);
        }
    }

    @Override
    @Nullable
    public Geometry convertToEntityValue(@Nullable String persistedValue, ConversionContext context) {
        if (StringUtils.isEmpty(persistedValue)) {
            return null;
        }
        GeoJson geoJson;
        try {
            geoJson = jsonMapper.readValue(persistedValue, GeoJson.class);
        } catch (IOException e) {
            throw new SerializationException("Failed to deserialize json [" + persistedValue + "]", e);
        }
        return geoJson == null ? null : getGeometry(geoJson);
    }

    private GeoJson getGeoJson(Geometry geometry) {
        return switch (geometry) {
            case Point point -> new PointGeoJson("Point", point.asCoords());
            case MultiPoint multiPoint -> new MultiPointGeoJson("MultiPoint", multiPoint.asCoords());
            case LineString lineString -> new LineStringGeoJson("LineString", lineString.asCoords());
            case MultiLineString multiLineString -> new MultiLineStringGeoJson("MultiLineString", multiLineString.asCoords());
            case Polygon polygon -> new PolygonGeoJson("Polygon", polygon.asCoords());
            case MultiPolygon multiPolygon -> new MultiPolygonGeoJson("MultiPolygon", multiPolygon.asCoords());
            case GeometryCollection geometryCollection -> getGeoJsonCollection(geometryCollection);
        };
    }

    private GeoJsonCollection getGeoJsonCollection(GeometryCollection geometryCollection) {
        List<GeoJson> geoJsons = new ArrayList<>();
        geometryCollection.geometries().forEach(geometry -> {
            if (geometry instanceof GeometryCollection nestedGeometryCollection) {
                geoJsons.add(getGeoJsonCollection(nestedGeometryCollection));
            } else {
                geoJsons.add(getGeoJson(geometry));
            }
        });
        return new GeoJsonCollection("GeometryCollection", geoJsons);
    }

    private Geometry getGeometry(GeoJson geoJson) {
        return switch (geoJson) {
            case PointGeoJson pointGeoJson -> Point.fromCoords(pointGeoJson.coordinates());
            case MultiPointGeoJson multiPointGeoJson -> MultiPoint.fromCoords(multiPointGeoJson.coordinates());
            case LineStringGeoJson lineStringGeoJson -> LineString.fromCoords(lineStringGeoJson.coordinates());
            case MultiLineStringGeoJson multiLineStringGeoJson -> MultiLineString.fromCoords(multiLineStringGeoJson.coordinates());
            case PolygonGeoJson polygonGeoJson -> Polygon.fromCoords(polygonGeoJson.coordinates());
            case MultiPolygonGeoJson multiPolygonGeoJson -> MultiPolygon.fromCoords(multiPolygonGeoJson.coordinates());
            case GeoJsonCollection geoJsonCollection -> getGeometryCollection(geoJsonCollection);
        };
    }

    private Geometry getGeometryCollection(GeoJsonCollection geoJsonCollection) {
        List<Geometry> geometries = new ArrayList<>();
        geoJsonCollection.geometries.forEach(geoJson -> {
            if (geoJson instanceof GeoJsonCollection nestedGeoJsonCollection) {
                geometries.add(getGeometryCollection(nestedGeoJsonCollection));
            } else {
                geometries.add(getGeometry(geoJson));
            }
        });
        return new GeometryCollection(geometries);
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
    @JsonSubTypes({
        @JsonSubTypes.Type(value = PointGeoJson.class, name = "Point"),
        @JsonSubTypes.Type(value = MultiPointGeoJson.class, name = "MultiPoint"),
        @JsonSubTypes.Type(value = LineStringGeoJson.class, name = "LineString"),
        @JsonSubTypes.Type(value = MultiLineStringGeoJson.class, name = "MultiLineString"),
        @JsonSubTypes.Type(value = PolygonGeoJson.class, name = "Polygon"),
        @JsonSubTypes.Type(value = MultiPolygonGeoJson.class, name = "MultiPolygon"),
        @JsonSubTypes.Type(value = GeoJsonCollection.class, name = "GeometryCollection")
    })
    sealed interface GeoJson permits PointGeoJson, MultiPointGeoJson, LineStringGeoJson,
        MultiLineStringGeoJson, PolygonGeoJson, GeoJsonCollection, MultiPolygonGeoJson {}

    @Serdeable
    record PointGeoJson(String type, List<Double> coordinates) implements GeoJson {}

    @Serdeable
    record MultiPointGeoJson(String type, List<List<Double>> coordinates) implements GeoJson {}

    @Serdeable
    record LineStringGeoJson(String type, List<List<Double>> coordinates) implements GeoJson {}

    @Serdeable
    record MultiLineStringGeoJson(String type, List<List<List<Double>>> coordinates) implements GeoJson {}

    @Serdeable
    record PolygonGeoJson(String type, List<List<List<Double>>> coordinates) implements GeoJson {}

    @Serdeable
    record MultiPolygonGeoJson(String type, List<List<List<List<Double>>>> coordinates) implements GeoJson {}

    @Serdeable
    record GeoJsonCollection(String type, List<GeoJson> geometries) implements GeoJson {}
}

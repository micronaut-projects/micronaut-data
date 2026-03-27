package io.micronaut.data.model.runtime.convert;

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
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Converts {@link Geometry} values to and from their Well-Known Text (WKT) representation.
 *
 * <p>This converter supports the Micronaut Data geo model hierarchy and handles nested
 * structures such as {@link GeometryCollection} values during formatting and parsing.
 *
 * @since 5.0
 */
@Singleton
public final class GeometryWktConverter implements AttributeConverter<Geometry, String> {

    @Override
    @Nullable
    public String convertToPersistedValue(@Nullable Geometry entityValue, ConversionContext context) {
        if (entityValue == null) {
            return null;
        }
        return formatGeometry(entityValue);
    }

    @Override
    @Nullable
    public Geometry convertToEntityValue(@Nullable String persistedValue, ConversionContext context) {
        if (StringUtils.isEmpty(persistedValue)) {
            return null;
        }
        try {
            return parseGeometry(persistedValue.trim());
        } catch (RuntimeException e) {
            throw new SerializationException("Failed to deserialize WKT [" + persistedValue + "]", e);
        }
    }

    private String formatGeometry(Geometry geometry) {
        return switch (geometry) {
            case Point point -> "POINT " + formatPoint(point, true);
            case MultiPoint multiPoint -> "MULTIPOINT " + formatPoints(multiPoint.points());
            case LineString lineString -> "LINESTRING " + formatPoints(lineString.points());
            case MultiLineString multiLineString -> "MULTILINESTRING " + formatLineStrings(multiLineString.lineStrings());
            case Polygon polygon -> "POLYGON " + formatLineStrings(polygon.lineStrings());
            case MultiPolygon multiPolygon -> "MULTIPOLYGON " + formatMultiPolygon(multiPolygon);
            case GeometryCollection geometryCollection -> "GEOMETRYCOLLECTION" + formatGeometryCollection(geometryCollection);
        };
    }

    private Geometry parseGeometry(String wkt) {
        int idx = wkt.indexOf('(');
        if (idx < 0) {
            throw new IllegalArgumentException("Invalid WKT: " + wkt);
        }
        String type = wkt.substring(0, idx).trim().toUpperCase();
        String body = wkt.substring(idx).trim();
        return switch (type) {
            case "POINT" -> Point.fromCoords(parsePoint(body));
            case "MULTIPOINT" -> MultiPoint.fromCoords(parsePoints(body));
            case "LINESTRING" -> LineString.fromCoords(parsePoints(body));
            case "MULTILINESTRING" -> MultiLineString.fromCoords(parseLineStrings(body));
            case "POLYGON" -> Polygon.fromCoords(parseLineStrings(body));
            case "MULTIPOLYGON" -> MultiPolygon.fromCoords(parseMultiPolygon(body));
            case "GEOMETRYCOLLECTION" -> new GeometryCollection(parseGeometryCollection(body));
            default -> throw new IllegalArgumentException("Unsupported WKT type: " + type);
        };
    }

    private String formatNumber(double value) {
        // Keep whole-number doubles as "1" instead of "1.0" so the generated WKT stays compact and stable.
        // Math.rint is used as the integral-value check rather than modulo arithmetic on doubles.
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    private String formatPoint(Point point, boolean useParentheses) {
        String wktPoint = formatNumber(point.x()) + ' ' + formatNumber(point.y());
        return useParentheses ? '(' + wktPoint + ')' : wktPoint;
    }

    private String formatPoints(List<Point> points) {
        String formatted = points.stream()
            .map(point -> formatPoint(point, false))
            .collect(Collectors.joining(", "));
        return addOuterParentheses(formatted);
    }

    private String formatLineStrings(List<LineString> lineStrings) {
        String formatted = lineStrings.stream()
            .map(lineString -> formatPoints(lineString.points()))
            .collect(Collectors.joining(", "));
        return addOuterParentheses(formatted);
    }

    private String formatMultiPolygon(MultiPolygon multiPolygon) {
        String formatted = multiPolygon.polygons()
            .stream()
            .map(polygon -> formatLineStrings(polygon.lineStrings()))
            .collect(Collectors.joining(", "));
        return addOuterParentheses(formatted);
    }

    private String formatGeometryCollection(GeometryCollection geometryCollection) {
        String formatted = geometryCollection.geometries()
            .stream()
            .map(this::formatGeometry)
            .collect(Collectors.joining(", "));
        return addOuterParentheses(formatted);
    }

    private List<Double> parsePoint(String value) {
        String stripped = stripOuterParentheses(value);
        return StringUtils.splitOmitEmptyStringsList(stripped, ' ')
            .stream()
            .map(Double::parseDouble)
            .toList();
    }

    private List<List<Double>> parsePoints(String value) {
        String stripped = stripOuterParentheses(value);
        return splitByTopLevelCommas(stripped)
            .stream()
            .map(String::trim)
            .map(this::parsePoint)
            .toList();
    }

    private List<List<List<Double>>> parseLineStrings(String value) {
        String stripped = stripOuterParentheses(value);
        return splitByTopLevelCommas(stripped)
            .stream()
            .map(String::trim)
            .map(this::parsePoints)
            .toList();
    }

    private List<List<List<List<Double>>>> parseMultiPolygon(String value) {
        String stripped = stripOuterParentheses(value);
        return splitByTopLevelCommas(stripped)
            .stream()
            .map(String::trim)
            .map(this::parseLineStrings)
            .toList();
    }

    private List<Geometry> parseGeometryCollection(String value) {
        String stripped = stripOuterParentheses(value);
        return splitByTopLevelCommas(stripped)
            .stream()
            .map(String::trim)
            .map(this::parseGeometry)
            .toList();
    }

    private String stripOuterParentheses(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private String addOuterParentheses(String value) {
        return '(' + value + ')';
    }

    private List<String> splitByTopLevelCommas(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth < 0) {
                    throw new IllegalArgumentException("Unbalanced parentheses at index " + i);
                }
            } else if (c == ',' && depth == 0) {
                String part = s.substring(start, i).trim();
                if (!part.isEmpty()) {
                    out.add(part);
                }
                start = i + 1;
            }
        }
        if (depth != 0) {
            throw new IllegalArgumentException("Unbalanced parentheses");
        }
        String last = s.substring(start).trim();
        if (!last.isEmpty()) {
            out.add(last);
        }
        return out;
    }
}

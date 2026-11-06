package io.micronaut.data.neo4j.features;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

@Singleton
public class PointsConverter implements AttributeConverter<Event.Points, Integer> {

    @Override
    public @Nullable Integer convertToPersistedValue(Event.@Nullable Points points, ConversionContext context) {
        return points == null ? null : points.value() * 10;
    }

    @Override
    public Event.@Nullable Points convertToEntityValue(@Nullable Integer value, ConversionContext context) {
        return value == null ? null : new Event.Points(value / 10);
    }
}

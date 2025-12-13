package io.micronaut.data.r2dbc.convert.vendor;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConvertor;
import io.micronaut.data.model.vector.Vector;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Internal
@Singleton
@Named("POSTGRES")
@Requires(classes = io.r2dbc.postgresql.codec.Vector.class)
public class PostgresJdbcVectorConvertor implements VectorTypeConvertor<io.r2dbc.postgresql.codec.Vector> {

    private final ConversionService conversionService;

    public PostgresJdbcVectorConvertor(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Class<io.r2dbc.postgresql.codec.Vector> getPersistedType() {
        return io.r2dbc.postgresql.codec.Vector.class;
    }

    @Override
    public io.r2dbc.postgresql.codec.Vector convert(Vector vector, Class<io.r2dbc.postgresql.codec.Vector> targetType) {
        return conversionService.convert(vector, targetType).get();
    }

    @Override
    public Vector convert(io.r2dbc.postgresql.codec.Vector object, Class<Vector> targetType) {
        return conversionService.convert(object, targetType).get();
    }

    @Override
    public Dialect getDialect() {
        return Dialect.POSTGRES;
    }

    @Override
    public @NonNull String getName() {
        return getDialect().toString();
    }
}

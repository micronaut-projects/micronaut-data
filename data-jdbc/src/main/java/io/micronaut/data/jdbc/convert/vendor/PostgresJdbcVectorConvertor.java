package io.micronaut.data.jdbc.convert.vendor;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.runtime.convert.vector.VectorTypeConvertor;
import io.micronaut.data.model.vector.Vector;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.postgresql.util.PGobject;

@Internal
@Singleton
@Named("POSTGRES")
public class PostgresJdbcVectorConvertor implements VectorTypeConvertor<PGobject> {

    private final ConversionService conversionService;

    public PostgresJdbcVectorConvertor(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Class<PGobject> getPersistedType() {
        return PGobject.class;
    }

    @Override
    public PGobject convert(Vector vector, Class<PGobject> targetType) {
        return conversionService.convert(vector, targetType).get();
    }

    @Override
    public Vector convert(PGobject object, Class<Vector> targetType) {
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

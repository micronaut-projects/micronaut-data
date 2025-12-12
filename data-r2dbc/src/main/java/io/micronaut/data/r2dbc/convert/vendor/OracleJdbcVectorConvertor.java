package io.micronaut.data.r2dbc.convert.vendor;

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
@Named("ORACLE")
public class OracleJdbcVectorConvertor implements VectorTypeConvertor<String> {

    private final ConversionService conversionService;

    public OracleJdbcVectorConvertor(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Class<String> getPersistedType() {
        return String.class;
    }

    @Override
    public String convert(Vector vector, Class<String> targetType) {
        return conversionService.convert(vector, targetType).get();
    }

    @Override
    public Vector convert(String object, Class<Vector> targetType) {
        return conversionService.convert(object, targetType).get();
    }

    @Override
    public Dialect getDialect() {
        return Dialect.ORACLE;
    }

    @Override
    public @NonNull String getName() {
        return getDialect().toString();
    }
}

package io.micronaut.data.model.runtime.convert;

import io.micronaut.context.annotation.Requires;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonTextObjectMapper;
import jakarta.inject.Singleton;

/**
 * Exposes the Oracle JDBC JSON text mapper as a {@link JsonMapper} bean for runtime converters.
 *
 * <p>This bean is only loaded when Oracle JDBC JSON support is available.</p>
 */
@Requires(classes = OracleJdbcJsonTextObjectMapper.class)
@Singleton
final class OracleJsonMapper {

    private final JsonMapper jsonMapper;

    OracleJsonMapper(OracleJdbcJsonTextObjectMapper oracleJdbcJsonTextObjectMapper) {
        jsonMapper = oracleJdbcJsonTextObjectMapper;
    }

    JsonMapper getJsonMapper() {
        return jsonMapper;
    }
}

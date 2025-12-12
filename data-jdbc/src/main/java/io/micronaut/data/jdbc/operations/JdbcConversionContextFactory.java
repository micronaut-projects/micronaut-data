package io.micronaut.data.jdbc.operations;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.data.jdbc.config.DataJdbcConfiguration;
import io.micronaut.data.model.runtime.convert.DialectConversionContext;
import io.micronaut.data.runtime.convert.ConversionContextFactory;

@EachBean(DataJdbcConfiguration.class)
@Internal
class JdbcConversionContextFactory implements ConversionContextFactory {

    private final DataJdbcConfiguration jdbcConfiguration;

    JdbcConversionContextFactory(@Parameter DataJdbcConfiguration  jdbcConfiguration) {
        this.jdbcConfiguration = jdbcConfiguration;
    }

    @Override
    public DialectConversionContext forArgument(Argument<?> argument) {
        return new DefaultJdbcRepositoryOperations.ArgumentJdbcCC(null, jdbcConfiguration.getDialect(), argument);

    }
}

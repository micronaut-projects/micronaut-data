package io.micronaut.data.r2dbc.operations;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.runtime.convert.DialectConversionContext;
import io.micronaut.data.r2dbc.config.DataR2dbcConfiguration;
import io.micronaut.data.runtime.convert.ConversionContextFactory;

import javax.sql.DataSource;


@EachBean(DataR2dbcConfiguration.class)
@Internal
class R2dbcConversionContextFactory implements ConversionContextFactory {

    private final DataR2dbcConfiguration r2dbcConfiguration;

    R2dbcConversionContextFactory(@Parameter DataR2dbcConfiguration  r2dbcConfiguration) {
        this.r2dbcConfiguration = r2dbcConfiguration;
    }

    @Override
    public DialectConversionContext forArgument(Argument<?> argument) {
        return new DefaultR2dbcRepositoryOperations.ArgumentR2dbcCC(null, r2dbcConfiguration.getDialect(), argument);

    }
}

/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.jdbc.operations;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.data.jdbc.config.DataJdbcConfiguration;
import io.micronaut.data.model.runtime.convert.DialectConversionContext;
import io.micronaut.data.runtime.convert.ConversionContextFactory;

/**
 * Factory creating JDBC-specific {@link io.micronaut.data.model.runtime.convert.DialectConversionContext}
 * instances enriched with the configured SQL {@link io.micronaut.data.model.query.builder.sql.Dialect}.
 *
 * @author Nemanja Mikic
 * @since 5.0.0
 */
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

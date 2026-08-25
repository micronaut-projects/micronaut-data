/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.r2dbc.operations;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.runtime.QueryOutParameterBinding;
import io.micronaut.data.runtime.operations.internal.sql.SqlStoredQuery;
import io.r2dbc.spi.Parameters;
import io.r2dbc.spi.R2dbcType;
import io.r2dbc.spi.Statement;

import java.util.List;

/**
 * Shared binding support for Oracle DML RETURNING OUT parameters.
 */
final class OracleR2dbcReturningSupport {

    private OracleR2dbcReturningSupport() {
    }

    static Statement bindOracleReturningOutParameters(Statement statement, SqlStoredQuery<?, ?> storedQuery, int startIndex) {
        List<QueryOutParameterBinding> outParameterBindings = storedQuery.getOutParameterBindings();
        if (CollectionUtils.isEmpty(outParameterBindings)) {
            throw new DataAccessException("Missing OUT parameter metadata for Oracle RETURNING. SqlQueryBuilder must attach QueryOutParameterBinding list.");
        }
        int index = startIndex;
        for (QueryOutParameterBinding outParameterBinding : outParameterBindings) {
            statement.bind(index++, Parameters.out(findR2dbcType(outParameterBinding.dataType())));
        }
        return statement;
    }

    private static R2dbcType findR2dbcType(DataType dataType) {
        return switch (dataType) {
            case BOOLEAN -> R2dbcType.BOOLEAN;
            case BYTE, SHORT, INTEGER -> R2dbcType.INTEGER;
            case LONG -> R2dbcType.BIGINT;
            case FLOAT -> R2dbcType.REAL;
            case DOUBLE -> R2dbcType.DOUBLE;
            case BIGDECIMAL -> R2dbcType.NUMERIC;
            case BYTE_ARRAY -> R2dbcType.VARBINARY;
            case DATE -> R2dbcType.DATE;
            case TIME -> R2dbcType.TIME;
            case TIMESTAMP -> R2dbcType.TIMESTAMP;
            case CHARACTER -> R2dbcType.CHAR;
            case BOOLEAN_ARRAY, CHARACTER_ARRAY, DOUBLE_ARRAY,
                 FLOAT_ARRAY, INTEGER_ARRAY, LONG_ARRAY, SHORT_ARRAY,
                 STRING_ARRAY -> R2dbcType.COLLECTION;
            default -> R2dbcType.VARCHAR;
        };
    }
}

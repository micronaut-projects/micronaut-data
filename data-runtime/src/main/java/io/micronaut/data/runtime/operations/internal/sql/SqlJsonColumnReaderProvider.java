/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.data.runtime.operations.internal.sql;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.runtime.QueryResultInfo;
import io.micronaut.data.runtime.mapper.sql.SqlJsonColumnReader;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * The provider for {@link SqlJsonColumnReader} when JSON columns are being read from {@link SqlPreparedQuery} results.
 *
 * @author radovanradic
 * @since 4.0.0
 *
 * @param <RS> the result set type
 */
@Internal
@Singleton
public class SqlJsonColumnReaderProvider<RS> {

    private static final Logger LOG = LoggerFactory.getLogger(SqlJsonColumnReaderProvider.class);

    private final SqlJsonColumnReader<RS> defaultSqlJsonColumnReader;
    private final List<SqlJsonColumnReader<RS>> sqlJsonColumnReaders;

    /**
     * Default constructor.
     *
     * @param jsonMapper the default JSON mapper
     * @param sqlJsonColumnReaders list of custom SQL JSON column readers
     */
    public SqlJsonColumnReaderProvider(@Nullable JsonMapper jsonMapper, List<SqlJsonColumnReader<RS>> sqlJsonColumnReaders) {
        this.defaultSqlJsonColumnReader = createDefaultSqlJsonColumnReader(jsonMapper);
        this.sqlJsonColumnReaders = sqlJsonColumnReaders;
    }

    /**
     * Provides {@link SqlJsonColumnReader} for given SQL prepared query. If query is single column result producing JSON
     * and if there is specific {@link SqlJsonColumnReader} that supports given prepared query then it will be returned.
     * Otherwise, it will return default {@link SqlJsonColumnReader}. In case query doesn't produce single column of JSON type
     * it will return null;
     *
     * @param sqlPreparedQuery the SQL prepared query
     * @return the {@link SqlJsonColumnReader} for given SQL prepared query, or default @{@link SqlJsonColumnReader}
     * if prepared query does not have specific one that it supports
     */
    public SqlJsonColumnReader<RS> get(SqlPreparedQuery sqlPreparedQuery) {
        SqlJsonColumnReader<RS> supportedSqlJsonColumnReader = null;
        QueryResultInfo queryResultInfo = sqlPreparedQuery.getQueryResultInfo();
        if (queryResultInfo != null && queryResultInfo.getType() == io.micronaut.data.annotation.QueryResult.Type.JSON) {
            for (SqlJsonColumnReader<RS> sqlJsonColumnReader : sqlJsonColumnReaders) {
                if (sqlJsonColumnReader.supports(sqlPreparedQuery)) {
                    supportedSqlJsonColumnReader = sqlJsonColumnReader;
                    break;
                }
            }
        }

        if (supportedSqlJsonColumnReader != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Using custom JSON column reader for dialect {} and query result info {}",
                    sqlPreparedQuery.getDialect(), queryResultInfo);
            }
            return supportedSqlJsonColumnReader;
        }
        if (defaultSqlJsonColumnReader == null && LOG.isDebugEnabled()) {
            LOG.debug("No default SqlJsonColumnReader found for dialect {} and query result info {}. Need to add Micronaut JsonMapper to the classpath.",
                sqlPreparedQuery.getDialect(), queryResultInfo);
        }
        return defaultSqlJsonColumnReader;
    }

    /**
     * Provides default {@link SqlJsonColumnReader}.
     *
     * @return the default json column reader
     */
    public SqlJsonColumnReader<RS> getDefault() {
        return defaultSqlJsonColumnReader;
    }

    private SqlJsonColumnReader<RS> createDefaultSqlJsonColumnReader(JsonMapper jsonMapper) {
        if (jsonMapper == null) {
            return null;
        }
        return new SqlJsonColumnReader<RS>() {
            @Override
            public boolean supports(SqlPreparedQuery<?, ?> sqlPreparedQuery) {
                return true;
            }

            @Override
            public JsonMapper getJsonMapper() {
                return jsonMapper;
            }
        };
    }
}

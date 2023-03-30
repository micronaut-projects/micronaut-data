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
import io.micronaut.data.model.JsonType;
import io.micronaut.data.runtime.mapper.sql.SqlJsonColumnReader;
import io.micronaut.data.runtime.mapper.sql.SqlJsonValueMapper;
import io.micronaut.json.JsonMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * The provider for {@link SqlJsonValueMapper} when JSON columns are being written using {@link SqlStoredQuery}
 * and for {@link SqlJsonColumnReader} when JSON columns are being read from {@link SqlPreparedQuery} results.
 *
 * @author radovanradic
 * @since 4.0.0
 *
 * @param <RS> the reader result set type
 */
@Internal
@Singleton
public final class SqlJsonColumnMapperProvider<RS> {

    private static final Logger LOG = LoggerFactory.getLogger(SqlJsonColumnMapperProvider.class);

    private final List<SqlJsonColumnReader<RS>> sqlJsonColumnReaders;
    private final List<SqlJsonValueMapper> sqlJsonValueMappers;
    private final SqlJsonColumnReader<RS> defaultSqlJsonColumnReader;
    private final SqlJsonValueMapper defaultSqlJsonValueMapper;

    /**
     * Default constructor.
     *
     * @param jsonMapper the default JSON mapper
     * @param sqlJsonColumnReaders list of custom SQL JSON column readers
     * @param sqlJsonValueMappers  list of custom SQL JSON value mappers
     */
    public SqlJsonColumnMapperProvider(@Nullable JsonMapper jsonMapper, List<SqlJsonColumnReader<RS>> sqlJsonColumnReaders,
                                       List<SqlJsonValueMapper> sqlJsonValueMappers) {
        this.sqlJsonColumnReaders = sqlJsonColumnReaders;
        this.sqlJsonValueMappers = sqlJsonValueMappers;
        if (jsonMapper == null) {
            this.defaultSqlJsonColumnReader = null;
            this.defaultSqlJsonValueMapper = null;
        } else {
            this.defaultSqlJsonColumnReader = () -> jsonMapper;
            this.defaultSqlJsonValueMapper = () -> jsonMapper;
        }
    }

    /**
     * Provides {@link SqlJsonColumnReader} for given SQL prepared query. If there is specific {@link SqlJsonColumnReader}
     * that supports given prepared query and result set type then it will be returned.
     * Otherwise, it will return default {@link SqlJsonColumnReader}.
     *
     * @param sqlPreparedQuery the SQL prepared query
     * @param resultSetType the result set type (for R2Dbc and Jdbc it is different for example)
     * @return the {@link SqlJsonColumnReader} for given SQL prepared query, or default {@link SqlJsonColumnReader}
     * if prepared query does not have specific one that it supports
     */
    public SqlJsonColumnReader<RS> getJsonColumnReader(SqlPreparedQuery<?, ?> sqlPreparedQuery, Class<RS> resultSetType) {
        SqlJsonColumnReader<RS> supportedSqlJsonColumnReader = null;
        for (SqlJsonColumnReader<RS> sqlJsonColumnReader : sqlJsonColumnReaders) {
            if (sqlJsonColumnReader.supportsResultSetType(resultSetType) && sqlJsonColumnReader.supportsRead(sqlPreparedQuery)) {
                supportedSqlJsonColumnReader = sqlJsonColumnReader;
                break;
            }
        }

        if (supportedSqlJsonColumnReader != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Using custom JSON column reader for dialect",
                    sqlPreparedQuery.getDialect());
            }
            return supportedSqlJsonColumnReader;
        }
        if (defaultSqlJsonColumnReader == null && LOG.isDebugEnabled()) {
            LOG.debug("No default SqlJsonColumnReader found for dialect {}. Need to add Micronaut JsonMapper to the classpath.",
                sqlPreparedQuery.getDialect());
        }
        return defaultSqlJsonColumnReader;
    }

    /**
     * Provides {@link SqlJsonValueMapper} for given SQL stored query. If there is specific {@link SqlJsonValueMapper} that supports given stored query then it will be returned.
     * Otherwise, it will return default {@link SqlJsonValueMapper}.
     *
     * @param sqlStoredQuery the SQL stored query
     * @param jsonType the JSON representation type
     * @param value the value to be mapped
     * @return the {@link SqlJsonValueMapper} for given SQL stored query, or default {@link SqlJsonValueMapper}
     * if stored query does not have specific one that it supports
     */
    public SqlJsonValueMapper getJsonValueMapper(SqlStoredQuery<?, ?> sqlStoredQuery, JsonType jsonType, Object value) {
        if (value == null || value.getClass().equals(String.class)) {
            return defaultSqlJsonValueMapper;
        }
        SqlJsonValueMapper supportedSqlJsonValueMapper = null;
        for (SqlJsonValueMapper sqlJsonValueMapper : sqlJsonValueMappers) {
            if (sqlJsonValueMapper.supportsMapValue(sqlStoredQuery, jsonType)) {
                supportedSqlJsonValueMapper = sqlJsonValueMapper;
                break;
            }
        }

        if (supportedSqlJsonValueMapper != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Using custom JSON column value mapper for dialect {}",  sqlStoredQuery.getDialect());
            }
            return supportedSqlJsonValueMapper;
        }
        if (defaultSqlJsonValueMapper == null && LOG.isDebugEnabled()) {
            LOG.debug("No default SqlJsonValueMapper found for dialect {}. Need to add Micronaut JsonMapper to the classpath.",
                sqlStoredQuery.getDialect());
        }
        return defaultSqlJsonValueMapper;
    }
}

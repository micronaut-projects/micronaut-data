/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.jdbc.config;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.NextMajorVersion;
import io.micronaut.data.annotation.Fetch;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.naming.Named;
import io.micronaut.core.util.Toggleable;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlDialectOptions;
import io.micronaut.data.runtime.config.SchemaGenerate;
import io.micronaut.data.runtime.config.SqlDialectOptionsConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for Data JDBC.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@EachProperty(value = DataJdbcConfiguration.PREFIX, primary = "default")
public class DataJdbcConfiguration implements Named, Toggleable {
    /**
     * The prefix to use.
     */
    public static final String PREFIX = "datasources";

    private SchemaGenerate schemaGenerate = SchemaGenerate.NONE;
    private boolean batchGenerate = false;
    private Dialect dialect = Dialect.ANSI;
    @ConfigurationBuilder(prefixes = "set", configurationPrefix = "dialect-options")
    private DialectOptionsConfiguration dialectOptions = new DialectOptionsConfiguration();
    private List<String> packages = new ArrayList<>(3);
    private final String name;
    @Nullable
    private String schemaGenerateName;
    @Nullable
    private List<String> schemaGenerateNames;

    /**
     * If true, {@link javax.sql.DataSource#getConnection()} will be used in try-resource block for the operation.
     */
    private boolean allowConnectionPerOperation = true;
    private boolean enabled = true;

    /**
     * Fail on multiple results for findOne.
     */
    @NextMajorVersion("Make the default")
    private boolean uniqueResultOnFindOne;

    @NonNull
    private Integer defaultFetchSize = Fetch.DEFAULT_FETCH_SIZE;

    /**
     * The configuration.
     * @param name The configuration name
     */
    public DataJdbcConfiguration(@Parameter String name) {
        this.name = name;
    }

    /**
     * @return The schema generation strategy.
     */
    public SchemaGenerate getSchemaGenerate() {
        return schemaGenerate;
    }

    /**
     * Sets the schema generation strategy.
     * @param schemaGenerate The schema generation strategy.
     */
    public void setSchemaGenerate(SchemaGenerate schemaGenerate) {
        if (schemaGenerate != null) {
            this.schemaGenerate = schemaGenerate;
        }
    }

    /**
     * @return Whether to generate tables in batch.
     */
    public boolean isBatchGenerate() {
        return batchGenerate;
    }

    /**
     * @param batchGenerate Whether to generate tables in batch.
     */
    public void setBatchGenerate(boolean batchGenerate) {
        this.batchGenerate = batchGenerate;
    }

    /**
     * @return The packages to include use for the purposes of schema generation.
     */
    public List<String> getPackages() {
        return packages;
    }

    /**
     * Sets the packages to include use for the purposes of schema generation.
     *
     * @param packages The packages
     */
    public void setPackages(List<String> packages) {
        if (packages != null) {
            this.packages = packages;
        }
    }

    /**
     * @return The dialect to use.
     */
    public Dialect getDialect() {
        return dialect;
    }

    /**
     * Sets the dialect.
     * @param dialect The dialect
     */
    public void setDialect(Dialect dialect) {
        this.dialect = dialect;
    }

    /**
     * @return The dialect options.
     */
    public DialectOptionsConfiguration getDialectOptions() {
        return dialectOptions;
    }

    /**
     * @param dialectOptions The dialect options.
     */
    public void setDialectOptions(@Nullable DialectOptionsConfiguration dialectOptions) {
        if (dialectOptions != null) {
            this.dialectOptions = dialectOptions;
        }
    }

    /**
     * @return The resolved dialect options.
     */
    public SqlDialectOptions resolveDialectOptions() {
        return dialectOptions.toDialectOptions(dialect);
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    /**
     * @return true if property is set
     */
    public boolean isAllowConnectionPerOperation() {
        return allowConnectionPerOperation;
    }

    /**
     * @param allowConnectionPerOperation The property
     */
    public void setAllowConnectionPerOperation(boolean allowConnectionPerOperation) {
        this.allowConnectionPerOperation = allowConnectionPerOperation;
    }

    /**
     * @return The schema name that should be used for generating
     */
    @Nullable
    public String getSchemaGenerateName() {
        return schemaGenerateName;
    }

    /**
     * @param schemaGenerateName The schema name that should be used for generating
     */
    public void setSchemaGenerateName(@Nullable String schemaGenerateName) {
        this.schemaGenerateName = schemaGenerateName;
    }

    /**
     * @return The schema names that should be used for generating
     */
    @Nullable
    public List<String> getSchemaGenerateNames() {
        return schemaGenerateNames;
    }

    /**
     * @param schemaGenerateNames The schema names that should be used for generating
     */
    public void setSchemaGenerateNames(@Nullable List<String> schemaGenerateNames) {
        this.schemaGenerateNames = schemaGenerateNames;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets an indicator telling whether data source is enabled.
     * @param enabled an indicator telling whether data source is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return Is unique result required on find one
     */
    public boolean isUniqueResultOnFindOne() {
        return uniqueResultOnFindOne;
    }

    /**
     * @param uniqueResultOnFindOne Is unique result required on find one
     */
    public void setUniqueResultOnFindOne(boolean uniqueResultOnFindOne) {
        this.uniqueResultOnFindOne = uniqueResultOnFindOne;
    }

    /**
     * Gets the default fetch size for the JDBC driver. The fetch size is a hint to the JDBC driver
     * as to the number of rows that should be fetched from the database when more rows are needed.
     * If not set, the JDBC driver's default fetch size will be used.
     * Used in streaming operations.
     *
     * @return the default fetch size
     */
    public @NonNull Integer getDefaultFetchSize() {
        return defaultFetchSize;
    }

    /**
     * Sets the default fetch size for the JDBC driver. The fetch size is a hint to the JDBC driver
     * as to the number of rows that should be fetched from the database when more rows are needed.
     * If set to null, the JDBC driver's default fetch size will be used.
     * Used in streaming operations.
     *
     * @param defaultFetchSize the default fetch size
     */
    public void setDefaultFetchSize(@NonNull Integer defaultFetchSize) {
        this.defaultFetchSize = defaultFetchSize;
    }

    /**
     * SQL dialect options for JDBC schema generation.
     */
    public static final class DialectOptionsConfiguration extends SqlDialectOptionsConfiguration {

        private boolean validateVersion = true;

        /**
         * @return Whether JDBC should validate generated SQL target versions against the connected server version.
         * @since 5.1
         */
        public boolean isValidateVersion() {
            return validateVersion;
        }

        /**
         * @param validateVersion Whether JDBC should validate generated SQL target versions against the connected server version.
         * @since 5.1
         */
        public void setValidateVersion(boolean validateVersion) {
            this.validateVersion = validateVersion;
        }
    }
}

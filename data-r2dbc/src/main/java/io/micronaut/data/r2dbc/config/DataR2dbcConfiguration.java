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
package io.micronaut.data.r2dbc.config;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.Named;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.r2dbc.operations.R2dbcOperations;
import io.micronaut.data.runtime.config.SchemaGenerate;
import io.micronaut.r2dbc.BasicR2dbcProperties;
import io.r2dbc.spi.ConnectionFactory;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for Schema generation.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@EachProperty(value = BasicR2dbcProperties.PREFIX, primary = "default")
public class DataR2dbcConfiguration implements Named {

    private SchemaGenerate schemaGenerate = SchemaGenerate.NONE;
    private boolean batchGenerate = false;
    private Dialect dialect = Dialect.ANSI;
    private List<String> packages = new ArrayList<>(3);
    private final String name;
    private final ConnectionFactory connectionFactory;
    private final Provider<R2dbcOperations> r2dbcOperations;
    @Nullable
    private String schemaGenerateName;
    @Nullable
    private List<String> schemaGenerateNames;

    /**
     * The configuration.
     *
     * @param name              The configuration name
     * @param connectionFactory The connection factory
     * @param r2dbcOperations   The operations
     */
    public DataR2dbcConfiguration(@Parameter String name,
                                  @Parameter ConnectionFactory connectionFactory,
                                  @Parameter Provider<R2dbcOperations> r2dbcOperations) {
        this.name = name;
        this.connectionFactory = connectionFactory;
        this.r2dbcOperations = r2dbcOperations;
    }

    /**
     * @return The R2DBC operations.
     */
    public R2dbcOperations getR2dbcOperations() {
        return r2dbcOperations.get();
    }

    /**
     * @return The connection factory.
     */
    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    /**
     * @return The schema generation strategy.
     */
    public SchemaGenerate getSchemaGenerate() {
        return schemaGenerate;
    }

    /**
     * Sets the schema generation strategy.
     *
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
     *
     * @param dialect The dialect
     */
    public void setDialect(Dialect dialect) {
        this.dialect = dialect;
    }

    @NonNull
    @Override
    public String getName() {
        return name;
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
}

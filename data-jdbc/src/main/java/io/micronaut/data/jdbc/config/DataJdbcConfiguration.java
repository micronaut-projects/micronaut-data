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

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.Named;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.runtime.config.SchemaGenerate;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for Data JDBC.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@EachProperty(value = DataJdbcConfiguration.PREFIX, primary = "default")
public class DataJdbcConfiguration implements Named {
    /**
     * The prefix to use.
     */
    public static final String PREFIX = "datasources";

    private SchemaGenerate schemaGenerate = SchemaGenerate.NONE;
    private boolean batchGenerate = false;
    private Dialect dialect = Dialect.ANSI;
    private List<String> packages = new ArrayList<>(3);
    private final String name;
    @Nullable
    private String schemaGenerateName;
    @Nullable
    private List<String> schemaGenerateNames;

    /**
     * If true, {@link io.micronaut.transaction.TransactionOperations} will be used for the operation.
     * This should be false by default in v4 as it adds additional overhead.
     */
    private boolean transactionPerOperation = true;

    /**
     * If true, {@link javax.sql.DataSource#getConnection()} will be used in try-resource block for the operation.
     */
    private boolean allowConnectionPerOperation;

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

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    /**
     * @return true if property is set
     */
    public boolean isTransactionPerOperation() {
        return transactionPerOperation;
    }

    /**
     * @param transactionPerOperation The property
     */
    public void setTransactionPerOperation(boolean transactionPerOperation) {
        this.transactionPerOperation = transactionPerOperation;
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
}

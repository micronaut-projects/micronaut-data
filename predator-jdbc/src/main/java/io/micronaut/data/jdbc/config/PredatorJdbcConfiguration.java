package io.micronaut.data.jdbc.config;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.naming.Named;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.runtime.config.SchemaGenerate;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for Predator JDBC.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@EachProperty(value = PredatorJdbcConfiguration.PREFIX, primary = "default")
public class PredatorJdbcConfiguration implements Named {
    /**
     * The prefix to use.
     */
    public static final String PREFIX = "datasources";

    private SchemaGenerate schemaGenerate = SchemaGenerate.NONE;
    private boolean batchGenerate = false;
    private Dialect dialect = Dialect.ANSI;
    private List<String> packages = new ArrayList<>(3);
    private final String name;

    /**
     * The configuration.
     * @param name The configuration name
     */
    public PredatorJdbcConfiguration(@Parameter String name) {
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
     * Sets the packages to use.
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

    @Nonnull
    @Override
    public String getName() {
        return name;
    }
}

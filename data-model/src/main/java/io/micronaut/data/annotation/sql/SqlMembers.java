package io.micronaut.data.annotation.sql;

/**
 * Interface for meta annotation members specific to SQL.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public interface SqlMembers {

    /**
     * Meta annotation member to represent the schema. This is not an actual annotation member
     * because not all database types support the notion of a custom schema.
     */
    String SCHEMA = "schema";

    /**
     * Meta annotation member to represent the catalog. This is not an actual annotation member
     * because not all database types support the notion of a custom schema.
     */
    String CATALOG = "catalog";
}

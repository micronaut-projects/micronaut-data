package io.micronaut.data.jdbc.h2

import groovy.sql.Sql
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder

import javax.sql.DataSource

class H2Util {

    static void createTables(DataSource dataSource, Class...classes) {
        PersistentEntity[] entities = classes.collect{ PersistentEntity.of(it)} as PersistentEntity[]
        String sql = """
DROP ALL OBJECTS;
${new SqlQueryBuilder(Dialect.H2).buildCreateTables(entities)}
"""
        new Sql(dataSource).executeUpdate(sql)
    }
}

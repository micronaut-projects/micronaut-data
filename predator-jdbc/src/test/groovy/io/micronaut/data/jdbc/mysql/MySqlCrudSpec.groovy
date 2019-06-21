package io.micronaut.data.jdbc.mysql

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.tck.repositories.PersonRepository
import io.micronaut.data.tck.tests.AbstractCrudSpec
import org.testcontainers.containers.MySQLContainer
import spock.lang.AutoCleanup
import spock.lang.Shared

class MySqlCrudSpec extends AbstractCrudSpec {
    @Shared @AutoCleanup MySQLContainer mysql = new MySQLContainer<>()
    @Shared @AutoCleanup ApplicationContext context

    @Override
    PersonRepository getCrudRepository() {
        return context.getBean(MySqlPersonRepository)
    }

    @Override
    void init() {
        mysql.start()
        context = ApplicationContext.run(
                "datasources.default.url":mysql.getJdbcUrl(),
                "datasources.default.username":mysql.getUsername(),
                "datasources.default.password":mysql.getPassword(),
                "datasources.default.schema-generate": SchemaGenerate.CREATE,
                "datasources.default.dialect": Dialect.MYSQL
        )

    }
}

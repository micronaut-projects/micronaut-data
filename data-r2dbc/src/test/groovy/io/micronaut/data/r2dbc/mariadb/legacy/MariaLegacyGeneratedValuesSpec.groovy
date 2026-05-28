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
package io.micronaut.data.r2dbc.mariadb.legacy

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.test.extensions.junit5.annotation.ScopeNamingStrategy
import io.micronaut.test.extensions.junit5.annotation.TestResourcesScope
import io.micronaut.test.support.TestPropertyProvider
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Result
import io.r2dbc.spi.Statement
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@TestResourcesScope(namingStrategy = ScopeNamingStrategy.TestClassName)
class MariaLegacyGeneratedValuesSpec extends Specification implements TestPropertyProvider {

    private static final String DATASOURCE_NAME = "legacy"
    private static final String TABLE_NAME = "legacy_tr_person"
    private static final String INSERT_SQL = "INSERT INTO ${TABLE_NAME}(name, age, enabled) VALUES (?, ?, TRUE)"

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    ConnectionFactory connectionFactory = context.getBean(ConnectionFactory, Qualifiers.byName(DATASOURCE_NAME))

    @Shared
    MariaLegacyTrPersonRepository repository = context.getBean(MariaLegacyTrPersonRepository)

    @Override
    Map<String, Object> getProperties() {
        String prefix = "r2dbc.datasources.$DATASOURCE_NAME"
        return [
            (prefix + ".db-type")                     : "mariadb",
            (prefix + ".schema-generate")            : "NONE",
            (prefix + ".packages")                   : [getClass().package.name],
            (prefix + ".test-resources.resource-name"): "mariadb-legacy-r2dbc",
            "test-resources.containers.mariadb.image-name": "mariadb",
            "test-resources.containers.mariadb.image-tag" : "10.4"
        ]
    }

    void setupSpec() {
        createSchema()
    }

    void cleanupSpec() {
        dropSchema()
    }

    void setup() {
        repository.deleteAll().block()
    }

    void "raw batch returnGeneratedValues on MariaDB 10.4 via test resources returns all ids"() {
        when:
        def ids = batchInsertAndCollectIds(["Jeff", "James"])

        then:
        ids == [1L, 2L]
        repository.count().block() == 2
    }

    void "repository saveAll populates ids on MariaDB 10.4 via test resources"() {
        given:
        def people = [
            person("Leto"),
            person("Ghanima")
        ]

        when:
        def saved = repository.saveAll(people).collectList().block()

        then:
        saved*.id.every { it != null }
        people*.id.every { it != null }
        repository.count().block() == 2
    }

    private void createSchema() {
        executeStatement("CREATE TABLE $TABLE_NAME (id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(255) NOT NULL, age INT NOT NULL, enabled BOOLEAN NOT NULL)")
    }

    private void dropSchema() {
        executeStatement("DROP TABLE IF EXISTS $TABLE_NAME")
    }

    private void executeStatement(String sql) {
        Flux.usingWhen(connectionFactory.create(), connection ->
            Flux.from(connection.createStatement(sql).execute()).flatMap(Result::getRowsUpdated),
            { connection -> connection.close() }
        ).then().block()
    }

    private List<Long> batchInsertAndCollectIds(List<String> names) {
        return Flux.usingWhen(connectionFactory.create(), connection -> {
            Statement statement = connection.createStatement(INSERT_SQL).returnGeneratedValues("id")
            boolean first = true
            names.each { name ->
                if (first) {
                    first = false
                } else {
                    statement = statement.add()
                }
                statement.bind(0, name).bind(1, 0)
            }
            return Flux.from(statement.execute())
                .flatMap { Result result -> result.map((row, metadata) -> row.get(0, Long.class)) }
        }, { connection -> connection.close() }).collectList().block()
    }

    private static MariaLegacyTrPerson person(String name) {
        def person = new MariaLegacyTrPerson()
        person.name = name
        person.age = 0
        return person
    }
}

@MappedEntity("legacy_tr_person")
class MariaLegacyTrPerson {

    @Id
    @GeneratedValue
    Long id

    String name

    int age

    boolean enabled = true
}

@R2dbcRepository(value = "legacy", dialect = Dialect.MYSQL)
interface MariaLegacyTrPersonRepository extends ReactorCrudRepository<MariaLegacyTrPerson, Long> {
}

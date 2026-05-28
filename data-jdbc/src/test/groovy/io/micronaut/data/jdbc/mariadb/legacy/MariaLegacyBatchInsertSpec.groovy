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
package io.micronaut.data.jdbc.mariadb.legacy

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Insert
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.runtime.config.SchemaGenerate
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared
import spock.lang.Specification

class MariaLegacyBatchInsertSpec extends Specification {

    private static final String DATASOURCE_NAME = "legacy"
    private static final String IMAGE = "mariadb:5.5.64"

    @Shared
    MariaDBContainer<?> mariaDb = new MariaDBContainer<>(DockerImageName.parse(IMAGE))
        .withDatabaseName("legacy")
        .withUsername("test")
        .withPassword("test")
        .withCreateContainerCmdModifier { cmd ->
            cmd.withPlatform("linux/amd64")
        }

    @Shared
    ApplicationContext context

    @Shared
    MariaLegacyBatchBookRepository repository

    void setupSpec() {
        mariaDb.start()
        context = ApplicationContext.run(properties())
        repository = context.getBean(MariaLegacyBatchBookRepository)
    }

    void cleanupSpec() {
        context?.close()
        mariaDb?.stop()
    }

    void setup() {
        repository.deleteAll()
    }

    void "custom void insertAll batches generated-id inserts on MariaDB 5.5 without mutating input ids"() {
        given:
        def books = [
            new MariaLegacyBatchBook(title: "Solaris"),
            new MariaLegacyBatchBook(title: "Fiasco")
        ]

        when:
        repository.customInsertAll(books)

        then:
        repository.count() == 2
        books*.id == [null, null]
        repository.findAll()*.id.every { it != null }
        repository.findAll()*.title as Set == ["Solaris", "Fiasco"] as Set
    }

    void "saveAll populates ids on MariaDB 5.5 via generated-key fallback"() {
        given:
        def books = [
            new MariaLegacyBatchBook(title: "The Cyberiad"),
            new MariaLegacyBatchBook(title: "His Master's Voice")
        ]

        when:
        def saved = repository.saveAll(books)

        then:
        saved*.id.every { it != null }
        books*.id.every { it != null }
        repository.count() == 2
    }

    private Map<String, Object> properties() {
        String prefix = "datasources." + DATASOURCE_NAME
        [
            'datasources.default.enabled'      : false,
            (prefix + '.enabled')              : true,
            (prefix + '.dialect')              : Dialect.MYSQL.name(),
            (prefix + '.schema-generate')      : SchemaGenerate.CREATE.name(),
            (prefix + '.packages')             : [getClass().package.name],
            (prefix + '.url')                  : mariaDb.jdbcUrl,
            (prefix + '.username')             : mariaDb.username,
            (prefix + '.password')             : mariaDb.password,
            (prefix + '.driver-class-name')    : mariaDb.driverClassName
        ]
    }
}

@MappedEntity("maria_legacy_batch_book")
class MariaLegacyBatchBook {

    @Id
    @GeneratedValue
    Long id

    String title
}

@JdbcRepository(value = "legacy", dialect = Dialect.MYSQL)
interface MariaLegacyBatchBookRepository extends CrudRepository<MariaLegacyBatchBook, Long> {

    @Insert
    void customInsertAll(List<MariaLegacyBatchBook> entities)
}

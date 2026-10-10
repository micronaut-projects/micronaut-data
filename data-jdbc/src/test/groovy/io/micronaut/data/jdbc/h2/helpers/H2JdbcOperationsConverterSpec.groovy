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
package io.micronaut.data.jdbc.h2.helpers

import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.ConversionContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.jdbc.mapper.SqlResultConsumer
import io.micronaut.data.jdbc.runtime.JdbcOperations
import io.micronaut.data.model.DataType
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.runtime.convert.AttributeConverter
import io.micronaut.data.repository.CrudRepository
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Connection

/**
 * The public {@link JdbcOperations} read helpers and a result consumer's context apply the properties' attribute
 * converters, like repositories do.
 */
class H2JdbcOperationsConverterSpec extends Specification implements H2TestPropertyProvider {

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(getProperties())

    @Shared
    LabelledItemRepository repository = ctx.getBean(LabelledItemRepository)

    @Shared
    JdbcOperations jdbcOperations = ctx.getBean(JdbcOperations)

    @Shared
    TransactionOperations<Connection> transactionOperations = ctx.getBean(TransactionOperations)

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    @Override
    Map<String, String> getProperties() {
        return H2TestPropertyProvider.super.getProperties() + [
            'datasources.default.url': 'jdbc:h2:mem:jdbcOperationsConverter;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE'
        ]
    }

    def setup() {
        repository.save(new LabelledItem(label: new Label("a")))
    }

    def cleanup() {
        repository.deleteAll()
    }

    void "readEntity applies the attribute converter"() {
        when:
            LabelledItem item = transactionOperations.executeRead {
                jdbcOperations.prepareStatement("SELECT * FROM labelled_item") { statement ->
                    def resultSet = statement.executeQuery()
                    resultSet.next()
                    jdbcOperations.readEntity(resultSet, LabelledItem)
                }
            }
        then:
            item.label.value == "a"
    }

    void "entityStream applies the attribute converter"() {
        when:
            List<LabelledItem> items = transactionOperations.executeRead {
                jdbcOperations.prepareStatement("SELECT * FROM labelled_item") { statement ->
                    jdbcOperations.entityStream(statement.executeQuery(), LabelledItem).toList()
                }
            }
        then:
            items*.label*.value == ["a"]
    }

    void "a result consumer's readEntity applies the attribute converter"() {
        given:
            List<LabelledItem> read = []
        when:
            repository.queryWithConsumer({ LabelledItem item, context ->
                read << context.readEntity("other_", LabelledItem)
            } as SqlResultConsumer<LabelledItem>)
        then:
            read*.label*.value == ["a"]
    }
}

@JdbcRepository(dialect = Dialect.H2)
interface LabelledItemRepository extends CrudRepository<LabelledItem, Long> {

    @Query("SELECT l.*, l.id AS other_id, l.label AS other_label FROM labelled_item l")
    LabelledItem queryWithConsumer(SqlResultConsumer<LabelledItem> consumer)
}

@MappedEntity
class LabelledItem {
    @Id
    @GeneratedValue
    Long id

    @TypeDef(type = DataType.STRING, converter = LabelConverter)
    Label label
}

class Label {
    String value

    Label(String value) {
        this.value = value
    }
}

@Singleton
class LabelConverter implements AttributeConverter<Label, String> {

    @Override
    String convertToPersistedValue(Label entityValue, ConversionContext context) {
        return entityValue?.value
    }

    @Override
    Label convertToEntityValue(String persistedValue, ConversionContext context) {
        return persistedValue == null ? null : new Label(persistedValue)
    }
}

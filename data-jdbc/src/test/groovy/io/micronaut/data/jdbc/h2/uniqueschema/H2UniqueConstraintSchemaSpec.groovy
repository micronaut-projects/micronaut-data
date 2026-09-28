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
package io.micronaut.data.jdbc.h2.uniqueschema

import io.micronaut.context.ApplicationContext
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlDialectOptions
import io.micronaut.data.model.query.builder.sql.SqlSchemaUtils
import io.micronaut.data.model.query.builder.sql.validation.SchemaValidationResult
import io.micronaut.data.model.query.builder.sql.validation.SqlTableMappingValidator
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.model.schema.sql.metadata.SqlColumnMetadata
import io.micronaut.data.model.schema.sql.metadata.SqlTableMetadata
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Types

class H2UniqueConstraintSchemaSpec extends Specification {

    static final Map<String, String> PROPERTIES = [
            'datasources.default.url'            : 'jdbc:h2:mem:uniqueSchema;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1',
            'datasources.default.schema-generate': 'CREATE_DROP',
            'datasources.default.dialect'        : 'H2',
            'datasources.default.username'       : '',
            'datasources.default.password'       : '',
            'datasources.default.packages'       : 'io.micronaut.data.jdbc.h2.uniqueschema',
            'datasources.default.driverClassName': 'org.h2.Driver',
            'datasources.default.schema-generate-unique-constraints': 'true'
    ]

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(PROPERTIES)

    void 'unique columns and unique constraints are mapped to unique indexes'() {
        when:
        def entity = context.getBean(RuntimeEntityRegistry).getEntity(H2UniqueItem)
        def table = SqlSchemaUtils.getSqlTableMappings(entity, Dialect.H2).first()
        def indexes = table.uniqueConstraints()

        then:"The unique constraints are kept apart from the declared indexes"
        table.indexes().isEmpty()
        indexes.size() == 3
        indexes.collect { [it.name(), it.unique(), it.columns().toList()] }.containsAll([
                ['UK_H2_UNIQUE_ITEM_CODE', true, ['code']],
                ['uk_h2_unique_item_first_second', true, ['first_part', 'second_part']]
        ])

        and:"An unnamed constraint can use an embedded column and gets a bounded name"
        def unnamed = indexes.find { it.columns().toList() == ['address_street', 'first_part'] }
        unnamed.unique()
        unnamed.name().startsWith('UK_H2_UNIQUE_ITEM_')
        unnamed.name().length() <= 30
    }

    void 'unique indexes are created and validated'() {
        when:"The unique indexes are read from the database"
        Set<String> uniqueIndexes = [] as Set
        DelegatingDataSource.unwrapDataSource(context.getBean(DataSource)).connection.withCloseable { connection ->
            // The table name can be stored as declared (escaped) or in upper case
            ['h2_unique_item', 'H2_UNIQUE_ITEM'].each { tableName ->
                connection.metaData.getIndexInfo(null, null, tableName, true, true).withCloseable { rs ->
                    while (rs.next()) {
                        uniqueIndexes << rs.getString('INDEX_NAME').toLowerCase(Locale.ENGLISH)
                    }
                }
            }
        }

        then:
        uniqueIndexes.containsAll(['uk_h2_unique_item_code', 'uk_h2_unique_item_first_second'])
        uniqueIndexes.any { it.startsWith('uk_h2_unique_item_') && it != 'uk_h2_unique_item_code' && it != 'uk_h2_unique_item_first_second' }

        when:"The created schema is validated"
        ApplicationContext.run(PROPERTIES + ['datasources.default.schema-generate': 'VALIDATE']).close()

        then:
        noExceptionThrown()
    }

    void 'unique constraints are not created by default'() {
        given:
        def properties = PROPERTIES + [
                'datasources.default.url'                              : 'jdbc:h2:mem:uniqueSchemaDisabled;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE;DB_CLOSE_DELAY=-1',
                'datasources.default.schema-generate-unique-constraints': 'false'
        ]
        def disabledContext = ApplicationContext.run(properties)

        when:
        Set<String> uniqueIndexes = [] as Set
        DelegatingDataSource.unwrapDataSource(disabledContext.getBean(DataSource)).connection.withCloseable { connection ->
            ['h2_unique_item', 'H2_UNIQUE_ITEM'].each { tableName ->
                connection.metaData.getIndexInfo(null, null, tableName, true, true).withCloseable { rs ->
                    while (rs.next()) {
                        uniqueIndexes << rs.getString('INDEX_NAME').toLowerCase(Locale.ENGLISH)
                    }
                }
            }
        }

        then:"Only the primary key index is unique"
        uniqueIndexes.every { !it.startsWith('uk_') }

        when:"The schema is validated without the unique constraints"
        ApplicationContext.run(properties + ['datasources.default.schema-generate': 'VALIDATE']).close()

        then:
        noExceptionThrown()

        cleanup:
        disabledContext?.close()
    }

    void 'missing unique index is reported as a warning'() {
        given:
        def validator = context.getBeansOfType(SqlTableMappingValidator).find { it.supportedDialect == Dialect.H2 }
        def entity = context.getBean(RuntimeEntityRegistry).getEntity(H2UniqueItem)
        def mapping = SqlSchemaUtils.getSqlTableMappings(entity, Dialect.H2).first()
        def metadata = new SqlTableMetadata(null, null, 'h2_unique_item')
        metadata.addColumn(new SqlColumnMetadata('id', Types.BIGINT, 'BIGINT', 64, 0, false))
        metadata.addColumn(new SqlColumnMetadata('code', Types.VARCHAR, 'CHARACTER VARYING', 255, 0, false))
        metadata.addColumn(new SqlColumnMetadata('first_part', Types.VARCHAR, 'CHARACTER VARYING', 255, 0, false))
        metadata.addColumn(new SqlColumnMetadata('second_part', Types.VARCHAR, 'CHARACTER VARYING', 255, 0, false))
        metadata.addColumn(new SqlColumnMetadata('address_street', Types.VARCHAR, 'CHARACTER VARYING', 255, 0, true))
        metadata.setPrimaryKeyColumns(['id'])
        metadata.setIndexes([])
        def result = new SchemaValidationResult()

        when:
        validator.validateTable(mapping, metadata, SqlDialectOptions.defaults(Dialect.H2), result)

        then:"The unique constraints are not part of the table validation"
        !result.hasErrors()
        result.warnings.isEmpty()

        when:
        validator.validateUniqueConstraints(mapping, metadata, result)

        then:
        !result.hasErrors()
        result.warnings.size() == 3
        result.warnings.containsAll([
                'Unique constraint [UK_H2_UNIQUE_ITEM_CODE] on columns [code] not found in table [h2_unique_item]',
                'Unique constraint [uk_h2_unique_item_first_second] on columns [first_part, second_part] not found in table [h2_unique_item]'
        ])
        result.warnings.any { it.contains('on columns [address_street, first_part] not found') }
    }
}

@Entity
@Table(name = "h2_unique_item", uniqueConstraints = [
        @UniqueConstraint(name = "uk_h2_unique_item_first_second", columnNames = ["first_part", "second_part"]),
        @UniqueConstraint(columnNames = ["address_street", "first_part"])
])
class H2UniqueItem {

    @Id
    @GeneratedValue
    Long id

    @Column(unique = true, nullable = false)
    String code

    @Column(nullable = false)
    String firstPart

    @Column(nullable = false)
    String secondPart

    @Embedded
    H2UniqueAddress address
}

@Embeddable
class H2UniqueAddress {

    @Column(name = "address_street", nullable = true)
    String street
}

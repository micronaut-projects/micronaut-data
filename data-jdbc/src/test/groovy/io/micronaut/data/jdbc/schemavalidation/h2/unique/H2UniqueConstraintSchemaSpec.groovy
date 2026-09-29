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
package io.micronaut.data.jdbc.schemavalidation.h2.unique

import io.micronaut.context.ApplicationContext
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlDialectOptions
import io.micronaut.data.model.query.builder.sql.SqlSchemaUtils
import io.micronaut.data.model.query.builder.sql.validation.SchemaValidationResult
import io.micronaut.data.model.query.builder.sql.validation.SqlTableMappingValidator
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.model.schema.sql.metadata.SqlColumnMetadata
import io.micronaut.data.model.schema.sql.metadata.SqlIndexMetadata
import io.micronaut.data.model.schema.sql.metadata.SqlTableMetadata
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
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
            'datasources.default.packages'       : 'io.micronaut.data.jdbc.schemavalidation.h2.unique',
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
        indexes.collect { [it.name(), it.unique(), it.columns().toList()] }.contains(
                ['uk_h2_unique_item_first_second', true, ['first_part', 'second_part']]
        )

        and:"An unique column gets a bounded name with a hash"
        def code = indexes.find { it.columns().toList() == ['code'] }
        code.unique()
        code.name() ==~ /UK_H2_UNIQUE_ITEM_\w*_[0-9A-F]{8}/
        code.name().length() <= 30

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
        uniqueIndexes.contains('uk_h2_unique_item_first_second')
        uniqueIndexes.findAll { it ==~ /uk_h2_unique_item_\w+_[0-9a-f]{8}/ }.size() == 2

        when:"The created schema is validated"
        ApplicationContext.run(PROPERTIES + ['datasources.default.schema-generate': 'VALIDATE']).close()

        then:
        noExceptionThrown()
    }

    void 'unique join columns and table constraints on join columns are created and validated'() {
        given:
        def dataSource = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource))

        when:"The unique indexes of the join columns are read from the database"
        def uniqueIndexes = uniqueIndexColumns(dataSource, 'h2_join_unique_item')

        then:"The unique join column and the table constraint on the join column are unique indexes, the other join column is not"
        uniqueIndexes.values().toSet() == [['passport_id'], ['owner_id', 'code'], ['id']].toSet()
        uniqueIndexes.containsKey('uk_h2_join_unique_item_owner_code')

        when:"The created schema is validated"
        ApplicationContext.run(PROPERTIES + ['datasources.default.schema-generate': 'VALIDATE']).close()

        then:
        noExceptionThrown()

        when:"The unique index of the join column is missing"
        def validator = context.getBeansOfType(SqlTableMappingValidator).find { it.supportedDialect == Dialect.H2 }
        def mapping = SqlSchemaUtils.getSqlTableMappings(context.getBean(RuntimeEntityRegistry).getEntity(H2JoinUniqueItem), Dialect.H2).first()
        def metadata = new SqlTableMetadata(null, null, 'h2_join_unique_item')
        metadata.setIndexes([new SqlIndexMetadata('uk_h2_join_unique_item_owner_code', true, ['OWNER_ID', 'CODE'])])
        def result = new SchemaValidationResult()
        validator.validateUniqueConstraints(mapping, metadata, result)

        then:
        !result.hasErrors()
        result.warnings.size() == 1
        result.warnings.first() ==~ /Unique constraint \[UK_\w+_[0-9A-F]{8}\] on columns \[passport_id\] not found in table \[h2_join_unique_item\]/
    }

    private static Map<String, List<String>> uniqueIndexColumns(DataSource dataSource, String table) {
        Map<String, Map<Integer, String>> indexes = [:]
        dataSource.connection.withCloseable { connection ->
            // The table name can be stored as declared (escaped) or in upper case
            [table, table.toUpperCase(Locale.ENGLISH)].each { tableName ->
                connection.metaData.getIndexInfo(null, null, tableName, true, true).withCloseable { rs ->
                    while (rs.next()) {
                        indexes.computeIfAbsent(rs.getString('INDEX_NAME').toLowerCase(Locale.ENGLISH), k -> new TreeMap<>())
                            .put(rs.getInt('ORDINAL_POSITION'), rs.getString('COLUMN_NAME').toLowerCase(Locale.ENGLISH))
                    }
                }
            }
        }
        return indexes.collectEntries { name, columns -> [name, columns.values().toList()] }
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
        result.warnings.contains('Unique constraint [uk_h2_unique_item_first_second] on columns [first_part, second_part] not found in table [h2_unique_item]')
        result.warnings.any { it ==~ /Unique constraint \[UK_H2_UNIQUE_ITEM_\w+\] on columns \[code\] not found in table \[h2_unique_item\]/ }
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

@Entity
@Table(name = "h2_unique_owner")
class H2UniqueOwner {

    @Id
    @GeneratedValue
    Long id
}

@Entity
@Table(name = "h2_join_unique_item", uniqueConstraints = @UniqueConstraint(name = "uk_h2_join_unique_item_owner_code", columnNames = ["owner_id", "code"]))
class H2JoinUniqueItem {

    @Id
    @GeneratedValue
    Long id

    @Column(nullable = false)
    String code

    @OneToOne
    @JoinColumn(name = "passport_id", unique = true)
    H2UniqueOwner passport

    @ManyToOne
    H2UniqueOwner owner
}

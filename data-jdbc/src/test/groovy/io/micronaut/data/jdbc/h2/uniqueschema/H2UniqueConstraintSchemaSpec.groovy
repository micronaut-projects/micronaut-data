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
            'datasources.default.driverClassName': 'org.h2.Driver'
    ]

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(PROPERTIES)

    void 'unique columns and unique constraints are mapped to unique indexes'() {
        when:
        def entity = context.getBean(RuntimeEntityRegistry).getEntity(H2UniqueItem)
        def indexes = SqlSchemaUtils.getSqlTableMappings(entity, Dialect.H2).first().indexes()

        then:
        indexes.collect { [it.name(), it.unique(), it.columns().toList()] } as Set == [
                ['UK_H2_UNIQUE_ITEM_CODE', true, ['code']],
                ['uk_h2_unique_item_first_second', true, ['first_part', 'second_part']]
        ] as Set
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

        when:"The created schema is validated"
        ApplicationContext.run(PROPERTIES + ['datasources.default.schema-generate': 'VALIDATE']).close()

        then:
        noExceptionThrown()
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
        metadata.setPrimaryKeyColumns(['id'])
        metadata.setIndexes([])
        def result = new SchemaValidationResult()

        when:
        validator.validateTable(mapping, metadata, SqlDialectOptions.defaults(Dialect.H2), result)

        then:
        !result.hasErrors()
        result.warnings as Set == [
                'Unique index [UK_H2_UNIQUE_ITEM_CODE] on columns [code] not found in table [h2_unique_item]',
                'Unique index [uk_h2_unique_item_first_second] on columns [first_part, second_part] not found in table [h2_unique_item]'
        ] as Set
    }
}

@Entity
@Table(name = "h2_unique_item", uniqueConstraints = @UniqueConstraint(name = "uk_h2_unique_item_first_second", columnNames = ["first_part", "second_part"]))
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
}

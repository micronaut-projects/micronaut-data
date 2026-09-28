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
package io.micronaut.data.jdbc.uniquecolumns

import io.micronaut.data.annotation.Reservable
import io.micronaut.data.exceptions.MappingException
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.query.builder.sql.SqlSchemaUtils
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import spock.lang.Specification

class UniqueConstraintColumnsSpec extends Specification {

    void 'unique constraint column is resolved by the exact name when columns differ only by the case'() {
        when:
        def mapping = SqlSchemaUtils.getSqlTableMappings(new RuntimePersistentEntity(CaseUniqueItem), Dialect.POSTGRES).first()

        then:
        mapping.uniqueConstraints().size() == 1
        mapping.uniqueConstraints().first().columns() == ['code'] as String[]
    }

    void 'unique constraint column not matching a single column is kept as declared'() {
        when:"The name matches more columns case-insensitively or no column, the mapping must not fail since unique constraints are opt-in"
        def ambiguous = SqlSchemaUtils.getSqlTableMappings(new RuntimePersistentEntity(AmbiguousUniqueItem), Dialect.POSTGRES).first()
        def unknown = SqlSchemaUtils.getSqlTableMappings(new RuntimePersistentEntity(UnknownUniqueItem), Dialect.POSTGRES).first()

        then:
        ambiguous.uniqueConstraints().first().columns() == ['CODE'] as String[]
        unknown.uniqueConstraints().first().columns() == ['missing'] as String[]
    }

    void 'unique constraint columns are resolved by the property name and the quoted name'() {
        when:
        def mapping = SqlSchemaUtils.getSqlTableMappings(new RuntimePersistentEntity(PropertyUniqueItem), Dialect.POSTGRES).first()

        then:
        mapping.uniqueConstraints()*.columns() == [['first_name', 'last_name'] as String[], ['last_name'] as String[]]
    }

    void 'reservable column in a unique constraint fails only when the unique constraints are generated'() {
        given:
        def entity = new RuntimePersistentEntity(ReservableUniqueItem)

        when:"The mapping is resolved"
        SqlSchemaUtils.getSqlTableMappings(entity, Dialect.ORACLE)

        then:
        noExceptionThrown()

        when:"The unique constraint statements are built"
        new SqlQueryBuilder(Dialect.ORACLE).buildCreateUniqueConstraintStatements([], entity)

        then:
        def e = thrown(MappingException)
        e.message.contains('@Reservable column [quantity] of table [reservable_unique_item] cannot be indexed')
    }
}

@Entity
@Table(name = "unknown_unique_item", uniqueConstraints = @UniqueConstraint(columnNames = "missing"))
class UnknownUniqueItem {
    @Id
    Long id
    String code
}

@Entity
@Table(name = "property_unique_item", uniqueConstraints = [
        @UniqueConstraint(columnNames = ["firstName", "lastName"]),
        @UniqueConstraint(columnNames = "\"last_name\"")
])
class PropertyUniqueItem {
    @Id
    Long id
    String firstName
    String lastName
}

@Entity
@Table(name = "reservable_unique_item")
class ReservableUniqueItem {
    @Id
    Long id
    @Reservable
    @Column(unique = true)
    Integer quantity
}

@Entity
@Table(name = "case_unique_item", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
class CaseUniqueItem {
    @Id
    Long id
    @Column(name = "Code")
    String upperCode
    String code
}

@Entity
@Table(name = "ambiguous_unique_item", uniqueConstraints = @UniqueConstraint(columnNames = "CODE"))
class AmbiguousUniqueItem {
    @Id
    Long id
    @Column(name = "Code")
    String upperCode
    String code
}

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
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.query.builder.sql.SqlSchemaCreateOptions
import io.micronaut.data.model.query.builder.sql.SqlSchemaUtils
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinColumns
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import spock.lang.Specification

class UniqueConstraintColumnsSpec extends Specification {

    void 'unique join columns and table constraints on join columns are mapped'() {
        when:
        def mapping = SqlSchemaUtils.getSqlTableMappings(new RuntimePersistentEntity(JoinUniqueItem), Dialect.POSTGRES).first()

        then:"The unique join column is a single column constraint, the join column without unique is not unique"
        mapping.uniqueConstraints()*.columns() == [['passport_id'] as String[], ['owner_id', 'code'] as String[]]
        mapping.uniqueConstraints()[0].name() ==~ /UK_JOIN_UNIQUE_ITEM_\w+/
        mapping.uniqueConstraints()[1].name() == 'uk_join_unique_item_owner_code'
        mapping.uniqueConstraints().every { it.unique() }
    }

    void 'a unique join column of a composite join is a single column constraint'() {
        when:
        def mapping = SqlSchemaUtils.getSqlTableMappings(new RuntimePersistentEntity(CompositeJoinItem), Dialect.POSTGRES).first()

        then:"Both join columns are stored, only the one declared unique is a unique constraint"
        mapping.columns()*.name.containsAll(['owner_a', 'owner_b'])
        mapping.uniqueConstraints()*.columns() == [['owner_a'] as String[]]
    }

    void 'unique constraint column is resolved by the exact name when columns differ only by the case'() {
        when:
        def mapping = SqlSchemaUtils.getSqlTableMappings(new RuntimePersistentEntity(CaseUniqueItem), Dialect.POSTGRES).first()

        then:
        mapping.uniqueConstraints().size() == 1
        mapping.uniqueConstraints().first().columns() == ['code'] as String[]
    }

    void 'generated unique constraint names of columns differing only by the case are distinct'() {
        when:
        def mapping = SqlSchemaUtils.getSqlTableMappings(new RuntimePersistentEntity(DistinctUniqueNamesItem), Dialect.POSTGRES).first()
        def names = mapping.uniqueConstraints()*.name()

        then:
        names.size() == 2
        names.toSet().size() == 2
        names.every { it.length() <= 30 }
    }

    void 'generated unique constraint names of different column lists are distinct'() {
        when:"The column lists a_b and (a, b) have the same readable name"
        def mapping = SqlSchemaUtils.getSqlTableMappings(new RuntimePersistentEntity(ColumnListUniqueItem), Dialect.POSTGRES).first()
        def names = mapping.uniqueConstraints()*.name()

        then:
        mapping.uniqueConstraints()*.columns() == [['a_b'] as String[], ['a', 'b'] as String[]]
        names.toSet().size() == 2
        names.every { it.startsWith('UK_ITEM_A_B_') && it.length() <= 30 }
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

    void 'unique constraints are created after the table only when enabled by the options'() {
        given:
        def builder = new SqlQueryBuilder(Dialect.POSTGRES)
        def entity = new RuntimePersistentEntity(ColumnListUniqueItem)
        def options = SqlSchemaCreateOptions.DEFAULT.withUniqueConstraints(true)

        when:
        def defaultStatements = builder.buildCreateTableStatements([], [entity] as PersistentEntity[], Dialect.POSTGRES) as List<String>
        def statements = builder.buildCreateTableStatements([], [entity] as PersistentEntity[], Dialect.POSTGRES, options) as List<String>
        def singleStatements = builder.buildCreateTableStatements(entity, [], options) as List<String>
        def batch = builder.buildBatchCreateTableStatement([], options, entity)

        then:
        defaultStatements.every { !it.contains('UNIQUE INDEX') }
        statements.size() == defaultStatements.size() + 2
        statements.take(defaultStatements.size()) == defaultStatements
        statements.drop(defaultStatements.size()).every { it.startsWith('CREATE UNIQUE INDEX') }
        singleStatements.count { it.startsWith('CREATE UNIQUE INDEX') } == 2
        batch.count('CREATE UNIQUE INDEX') == 2
    }

    void 'reservable column in a unique constraint fails only when the unique constraints are generated'() {
        given:
        def entity = new RuntimePersistentEntity(ReservableUniqueItem)

        def builder = new SqlQueryBuilder(Dialect.ORACLE)

        when:"The mapping is resolved and the tables are created without the unique constraints"
        SqlSchemaUtils.getSqlTableMappings(entity, Dialect.ORACLE)
        def statements = builder.buildCreateTableStatements([], [entity] as PersistentEntity[], Dialect.ORACLE)

        then:
        noExceptionThrown()
        statements.every { !it.contains('UNIQUE INDEX') }

        when:"The tables are created with the unique constraints"
        builder.buildCreateTableStatements([], [entity] as PersistentEntity[], Dialect.ORACLE, SqlSchemaCreateOptions.DEFAULT.withUniqueConstraints(true))

        then:
        def e = thrown(MappingException)
        e.message.contains('@Reservable column [quantity] of table [reservable_unique_item] cannot be indexed')
    }
}

@Entity
@Table(name = "unique_owner")
class UniqueOwner {
    @Id
    Long id
}

@Entity
@Table(name = "join_unique_item", uniqueConstraints = @UniqueConstraint(name = "uk_join_unique_item_owner_code", columnNames = ["owner_id", "code"]))
class JoinUniqueItem {
    @Id
    Long id
    String code
    @OneToOne
    @JoinColumn(name = "passport_id", unique = true)
    UniqueOwner passport
    @ManyToOne
    UniqueOwner owner
}

@Embeddable
class CompositeOwnerId {
    Long a
    Long b
}

@Entity
@Table(name = "composite_owner")
class CompositeOwner {
    @EmbeddedId
    CompositeOwnerId id
}

@Entity
@Table(name = "composite_join_item")
class CompositeJoinItem {
    @Id
    Long id
    @ManyToOne
    @JoinColumns([
            @JoinColumn(name = "owner_a", referencedColumnName = "a", unique = true),
            @JoinColumn(name = "owner_b", referencedColumnName = "b")
    ])
    CompositeOwner owner
}

@Entity
@Table(name = "distinct_unique_item")
class DistinctUniqueNamesItem {
    @Id
    Long id
    @Column(name = "Code", unique = true)
    String upperCode
    @Column(unique = true)
    String code
}

@Entity
@Table(name = "item", uniqueConstraints = [
        @UniqueConstraint(columnNames = "a_b"),
        @UniqueConstraint(columnNames = ["a", "b"])
])
class ColumnListUniqueItem {
    @Id
    Long id
    @Column(name = "a_b")
    String ab
    String a
    String b
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

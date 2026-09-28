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

import io.micronaut.data.exceptions.MappingException
import io.micronaut.data.model.query.builder.sql.Dialect
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

    void 'unique constraint column matching more columns case-insensitively is rejected'() {
        when:
        SqlSchemaUtils.getSqlTableMappings(new RuntimePersistentEntity(AmbiguousUniqueItem), Dialect.POSTGRES)

        then:
        def e = thrown(MappingException)
        e.message.contains('Unique constraint column [CODE] matches the columns [Code, code]')
    }
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

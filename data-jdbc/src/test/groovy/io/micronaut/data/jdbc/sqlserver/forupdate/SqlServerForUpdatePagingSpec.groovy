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
package io.micronaut.data.jdbc.sqlserver.forupdate

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Relation
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.sqlserver.MSSQLTestPropertyProvider
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.transaction.TransactionOperations
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Connection

/**
 * On SQL Server the lock hint of a ForUpdate query follows the table name, so a runtime pageable or sort must be
 * appended at the end of the query, not in front of the hint.
 */
class SqlServerForUpdatePagingSpec extends Specification implements MSSQLTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext ctx = ApplicationContext.run(getProperties())

    @Shared
    LockedItemRepository repository = ctx.getBean(LockedItemRepository)

    @Shared
    LockedOwnerRepository ownerRepository = ctx.getBean(LockedOwnerRepository)

    @Shared
    TransactionOperations<Connection> transactionOperations = ctx.getBean(TransactionOperations)

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    def setup() {
        LockedOwner owner = ownerRepository.save(new LockedOwner(name: "owner"))
        repository.saveAll([new LockedItem(name: "b", seq: 1, owner: owner), new LockedItem(name: "a", seq: 2, owner: owner), new LockedItem(name: "c", seq: 3, owner: owner)])
    }

    def cleanup() {
        repository.deleteAll()
        ownerRepository.deleteAll()
    }

    void "a ForUpdate finder with a runtime pageable"() {
        when:
            List<LockedItem> items = transactionOperations.executeWrite {
                repository.findBySeqLessThanForUpdate(10, Pageable.from(0, 2, Sort.of(Sort.Order.asc("name"))))
            }
        then:
            items*.name == ["a", "b"]
    }

    void "a native query with a lock hint and a runtime pageable"() {
        when:
            List<LockedItem> items = transactionOperations.executeWrite {
                repository.findNativeLocked(10, Pageable.from(0, 2, Sort.of(Sort.Order.asc("name"))))
            }
        then:
            items*.name == ["a", "b"]
    }

    void "a ForUpdate finder with a runtime sort"() {
        when:
            List<LockedItem> items = transactionOperations.executeWrite {
                repository.findBySeqGreaterThanForUpdate(0, Sort.of(Sort.Order.desc("name")))
            }
        then:
            items*.name == ["c", "b", "a"]
    }

    void "a ForUpdate finder with a join and a runtime pageable"() {
        when: "the lock hint also follows the joined table"
            List<LockedItem> items = transactionOperations.executeWrite {
                repository.findByOwnerNameForUpdate("owner", Pageable.from(0, 2, Sort.of(Sort.Order.asc("name"))))
            }
        then:
            items*.name == ["a", "b"]
            items*.owner*.name == ["owner", "owner"]
    }
}

@JdbcRepository(dialect = Dialect.SQL_SERVER)
interface LockedItemRepository extends CrudRepository<LockedItem, Long> {

    List<LockedItem> findBySeqLessThanForUpdate(int seq, Pageable pageable)

    List<LockedItem> findBySeqGreaterThanForUpdate(int seq, Sort sort)

    @Join("owner")
    List<LockedItem> findByOwnerNameForUpdate(String name, Pageable pageable)

    @Query("SELECT * FROM locked_item locked_item_ WITH (UPDLOCK, ROWLOCK) WHERE locked_item_.seq < :seq")
    List<LockedItem> findNativeLocked(int seq, Pageable pageable)
}

@JdbcRepository(dialect = Dialect.SQL_SERVER)
interface LockedOwnerRepository extends CrudRepository<LockedOwner, Long> {
}

@MappedEntity
class LockedItem {
    @Id
    @GeneratedValue
    Long id

    String name

    int seq

    @Nullable
    @Relation(Relation.Kind.MANY_TO_ONE)
    LockedOwner owner
}

@MappedEntity
class LockedOwner {
    @Id
    @GeneratedValue
    Long id

    String name
}

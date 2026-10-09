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
package io.micronaut.data.jdbc.h2.cursor

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Relation
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.CursoredPage
import io.micronaut.data.model.CursoredPageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.async.AsyncCrudRepository
import io.micronaut.data.repository.jpa.JpaSpecificationExecutor
import io.micronaut.data.repository.jpa.async.AsyncJpaSpecificationExecutor
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.reactive.ReactorJpaSpecificationExecutor
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.sql.Connection
import java.util.concurrent.CompletableFuture
import java.util.function.Function

/**
 * Walks every page of a cursored query, forward and backward, and checks that each row is returned exactly once.
 */
class H2CursorPagingWalkSpec extends Specification implements H2TestPropertyProvider {

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(getProperties())

    @Shared
    CursorRowRepository repository = ctx.getBean(CursorRowRepository)

    @Shared
    CursorTeamRepository teamRepository = ctx.getBean(CursorTeamRepository)

    @Shared
    ConnectionOperations<Connection> connectionOperations = ctx.getBean(ConnectionOperations)

    @Shared
    CursorRowAsyncRepository asyncRepository = ctx.getBean(CursorRowAsyncRepository)

    @Shared
    CursorRowReactiveRepository reactiveRepository = ctx.getBean(CursorRowReactiveRepository)

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    @Override
    Map<String, String> getProperties() {
        return H2TestPropertyProvider.super.getProperties() + [
            'datasources.default.url': 'jdbc:h2:mem:cursorPagingWalk;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE'
        ]
    }

    def cleanup() {
        repository.deleteAll()
    }

    void "a sort plus the id returns every row once (#path)"() {
        given: "duplicate names across a page boundary, so the id decides within a name"
            def ids = save(["a", "a", "a", "b", "b"])
        when:
            def walked = walk(CursoredPageable.from(2, Sort.of(Sort.Order.asc("name"))), finders()[path])
        then:
            walked.forward == ids
            walked.backward == ids
        where:
            path << PATHS
    }

    void "a case-insensitive sort pages through every row (#path)"() {
        given:
            def ids = save(["a", "B", "c", "D"])
        when:
            def walked = walk(CursoredPageable.from(1, Sort.of(Sort.Order.asc("name", true))), finders()[path])
        then:
            walked.forward == ids
            walked.backward == ids
        where:
            path << PATHS
    }

    void "nulls last pages through the null group (#path)"() {
        given:
            def ids = save(["a", "b", null, null])
        when:
            def order = new Sort.Order("name", Sort.Order.Direction.ASC, false, Sort.Order.NullOrdering.LAST)
            def walked = walk(CursoredPageable.from(1, Sort.of(order)), finders()[path])
        then:
            walked.forward == ids
            walked.backward == ids
        where:
            path << PATHS
    }

    void "nulls first pages through the null group (#path)"() {
        given:
            def ids = save([null, null, "a", "b"])
        when:
            def order = new Sort.Order("name", Sort.Order.Direction.ASC, false, Sort.Order.NullOrdering.FIRST)
            def walked = walk(CursoredPageable.from(1, Sort.of(order)), finders()[path])
        then:
            walked.forward == ids
            walked.backward == ids
        where:
            path << PATHS
    }

    void "a descending sort pages through every row (#path)"() {
        given:
            def ids = save(["a", "a", "b", "b", "c"])
        when:
            def walked = walk(CursoredPageable.from(2, Sort.of(Sort.Order.desc("name"))), finders()[path])
        then: "the appended id stays ascending within a name"
            walked.forward == [ids[4], ids[2], ids[3], ids[0], ids[1]]
            walked.backward == walked.forward
        where:
            path << PATHS
    }

    void "mixed directions page through every row (#path)"() {
        given:
            def ids = save(["a", "a", "b", "b"])
        when:
            def walked = walk(CursoredPageable.from(1, Sort.of(Sort.Order.asc("name"), Sort.Order.desc("seq"))), finders()[path])
        then:
            walked.forward == [ids[1], ids[0], ids[3], ids[2]]
            walked.backward == walked.forward
        where:
            path << PATHS
    }

    void "a descending case-insensitive sort with nulls first pages through every row (#path)"() {
        given:
            def ids = save(["a", null, "B", null, "c"])
        when:
            def order = new Sort.Order("name", Sort.Order.Direction.DESC, true, Sort.Order.NullOrdering.FIRST)
            def walked = walk(CursoredPageable.from(1, Sort.of(order)), finders()[path])
        then:
            walked.forward == [ids[1], ids[3], ids[4], ids[2], ids[0]]
            walked.backward == walked.forward
        where:
            path << PATHS
    }

    void "a sort on an embedded property pages through every row (#path)"() {
        given:
            def ids = repository.saveAll(["y", "x", "y", "x"].withIndex().collect { String city, int seq ->
                new CursorRow(name: "n", seq: seq, detail: new CursorDetail(city: city))
            })*.id
        when:
            def walked = walk(CursoredPageable.from(1, Sort.of(Sort.Order.asc("detail.city"))), finders()[path])
        then:
            walked.forward == [ids[1], ids[3], ids[0], ids[2]]
            walked.backward == walked.forward
        where:
            path << PATHS
    }

    static final List<String> PATHS = ["SQL", "criteria", "async criteria", "reactive criteria"]

    private Map<String, Function<CursoredPageable, CursoredPage<CursorRow>>> finders() {
        return [
            "SQL"              : { CursoredPageable p -> repository.findBySeqGreaterThan(-1, p) } as Function,
            "criteria"         : { CursoredPageable p -> repository.findAll(PredicateSpecification.ALL, p) } as Function,
            "async criteria"   : { CursoredPageable p -> asyncRepository.findAllCursored(PredicateSpecification.ALL, p).join() } as Function,
            "reactive criteria": { CursoredPageable p -> reactiveRepository.findAllCursored(PredicateSpecification.ALL, p).block() } as Function
        ]
    }

    void "a native query with an IN list and OR keeps the cursor restriction"() {
        given:
            def ids = save(["a", "b", "c", "d"])
        when: "seq IN (0, 1) OR name = 'c' matches the first three rows"
            def walked = walk(CursoredPageable.from(1, Sort.unsorted())) { repository.findNative([0, 1], "c", it) }
        then:
            walked.forward == ids.take(3)
            walked.backward == ids.take(3)
    }

    void "a native query with #description keeps the cursor restriction"() {
        given:
            def ids = save(["a", "b", "c"])
        when:
            def walked = walk(CursoredPageable.from(1, Sort.of(Sort.Order.desc("name"))), finder)
        then:
            walked.forward == ids.reverse()
            walked.backward == ids.reverse()
        where:
            description                            | finder
            "a nested block comment"               | { CursoredPageable p -> repository.findNativeWithNestedComment(p) } as Function
            "a dollar-quoted string"               | { CursoredPageable p -> repository.findNativeWithDollarQuote(p) } as Function
            "a comment ended by a carriage return" | { CursoredPageable p -> repository.findNativeWithCarriageReturn(p) } as Function
            "a trailing line comment"              | { CursoredPageable p -> repository.findNativeWithTrailingComment(p) } as Function
    }

    void "a native query with FOR UPDATE keeps it at the end"() {
        given:
            def ids = save(["a", "b", "c"])
        when:
            def walked = connectionOperations.executeWrite {
                walk(CursoredPageable.from(1, Sort.unsorted())) { repository.findNativeForUpdate(it) }
            }
        then:
            walked.forward == ids
            walked.backward == ids
    }

    void "a pageable finder with a join and OR pages its pagination subquery"() {
        given:
            def team = teamRepository.save(new CursorTeam(name: "t"))
            def rows = ["a", "b", "c", "d"].withIndex().collect { String name, int seq ->
                new CursorRow(name: name, seq: seq, team: seq % 2 == 0 ? team : null)
            }
            def ids = repository.saveAll(rows)*.id
        when: "seq < 2 OR name = 'd' matches a, b and d"
            def walked = walk(CursoredPageable.from(1, Sort.of(Sort.Order.asc("name")))) { repository.findBySeqLessThanOrName(2, "d", it) }
        then:
            walked.forward == [ids[0], ids[1], ids[3]]
            walked.backward == walked.forward
        cleanup:
            repository.deleteAll()
            teamRepository.deleteAll()
    }

    void "a cursored query with a lock keeps FOR UPDATE at the end"() {
        given:
            def ids = save(["a", "b", "c"])
        when:
            def walked = connectionOperations.executeWrite {
                walk(CursoredPageable.from(1, Sort.unsorted())) { repository.findBySeqLessThanForUpdate(10, it) }
            }
        then:
            walked.forward == ids
            walked.backward == ids
    }

    private List<Long> save(List<String> names) {
        def rows = names.withIndex().collect { String name, int seq -> new CursorRow(name: name, seq: seq) }
        return repository.saveAll(rows)*.id
    }

    /**
     * Pages forward to the end, then backward from the last page, and returns the ids in sort order for both.
     */
    private static Map<String, List<Long>> walk(CursoredPageable first, Function<CursoredPageable, CursoredPage<CursorRow>> finder) {
        List<CursoredPage<CursorRow>> pages = []
        CursoredPage<CursorRow> page = finder.apply(first)
        pages << page
        int guard = 0
        while (page.hasNext() && page.content && guard++ < 20) {
            page = finder.apply(page.nextPageable())
            pages << page
        }
        List<Long> forward = pages.collectMany { it.content*.id }

        List<Long> backward = []
        page = pages.findAll { it.content }.last()
        backward.addAll(0, page.content*.id)
        guard = 0
        while (page.hasPrevious() && page.content && guard++ < 20) {
            page = finder.apply(page.previousPageable())
            backward.addAll(0, page.content*.id)
        }
        return [forward: forward, backward: backward]
    }
}

@JdbcRepository(dialect = Dialect.H2)
interface CursorRowRepository extends CrudRepository<CursorRow, Long>, JpaSpecificationExecutor<CursorRow> {

    CursoredPage<CursorRow> findAll(PredicateSpecification<CursorRow> spec, CursoredPageable pageable)

    CursoredPage<CursorRow> findBySeqGreaterThan(int seq, CursoredPageable pageable)

    CursoredPage<CursorRow> findBySeqLessThanForUpdate(int seq, CursoredPageable pageable)

    @Query(value = "SELECT * FROM cursor_row cursor_row_ WHERE cursor_row_.seq IN (:ranks) OR cursor_row_.name = :name",
        countQuery = "SELECT count(*) FROM cursor_row cursor_row_ WHERE cursor_row_.seq IN (:ranks) OR cursor_row_.name = :name")
    CursoredPage<CursorRow> findNative(List<Integer> ranks, String name, CursoredPageable pageable)

    @Query(value = "SELECT * FROM cursor_row cursor_row_ WHERE cursor_row_.seq >= 0 /* outer /* inner */ ( */",
        countQuery = "SELECT count(*) FROM cursor_row cursor_row_")
    CursoredPage<CursorRow> findNativeWithNestedComment(CursoredPageable pageable)

    @Query(value = 'SELECT * FROM cursor_row cursor_row_ WHERE cursor_row_.name <> $$(where$$',
        countQuery = "SELECT count(*) FROM cursor_row cursor_row_")
    CursoredPage<CursorRow> findNativeWithDollarQuote(CursoredPageable pageable)

    @Query(value = "SELECT * FROM cursor_row cursor_row_ -- a comment\rWHERE cursor_row_.seq >= 0",
        countQuery = "SELECT count(*) FROM cursor_row cursor_row_")
    CursoredPage<CursorRow> findNativeWithCarriageReturn(CursoredPageable pageable)

    @Query(value = "SELECT * FROM cursor_row cursor_row_ WHERE cursor_row_.seq >= 0 -- all rows",
        countQuery = "SELECT count(*) FROM cursor_row cursor_row_")
    CursoredPage<CursorRow> findNativeWithTrailingComment(CursoredPageable pageable)

    @Query(value = "SELECT * FROM cursor_row cursor_row_ WHERE cursor_row_.seq >= 0 FOR UPDATE",
        countQuery = "select count(*) from cursor_row cursor_row_")
    CursoredPage<CursorRow> findNativeForUpdate(CursoredPageable pageable)

    @Join(value = "team", type = Join.Type.LEFT_FETCH)
    CursoredPage<CursorRow> findBySeqLessThanOrName(int seq, String name, CursoredPageable pageable)
}

@JdbcRepository(dialect = Dialect.H2)
interface CursorTeamRepository extends CrudRepository<CursorTeam, Long> {
}

@JdbcRepository(dialect = Dialect.H2)
interface CursorRowAsyncRepository extends AsyncCrudRepository<CursorRow, Long>, AsyncJpaSpecificationExecutor<CursorRow> {

    CompletableFuture<CursoredPage<CursorRow>> findAllCursored(PredicateSpecification<CursorRow> spec, CursoredPageable pageable)
}

@JdbcRepository(dialect = Dialect.H2)
interface CursorRowReactiveRepository extends ReactorCrudRepository<CursorRow, Long>, ReactorJpaSpecificationExecutor<CursorRow> {

    Mono<CursoredPage<CursorRow>> findAllCursored(PredicateSpecification<CursorRow> spec, CursoredPageable pageable)
}

@MappedEntity
class CursorRow {
    @Id
    @GeneratedValue
    Long id

    @Nullable
    String name

    int seq

    @Nullable
    @Relation(Relation.Kind.EMBEDDED)
    CursorDetail detail

    @Nullable
    @Relation(Relation.Kind.MANY_TO_ONE)
    CursorTeam team
}

@MappedEntity
class CursorTeam {
    @Id
    @GeneratedValue
    Long id

    String name
}

@Embeddable
class CursorDetail {
    @Nullable
    String city
}

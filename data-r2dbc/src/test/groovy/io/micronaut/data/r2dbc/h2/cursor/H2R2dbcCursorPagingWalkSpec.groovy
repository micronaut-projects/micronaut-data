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
package io.micronaut.data.r2dbc.h2.cursor

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.model.CursoredPage
import io.micronaut.data.model.CursoredPageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.r2dbc.h2.H2TestPropertyProvider
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.reactive.ReactorJpaSpecificationExecutor
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.util.function.Function

/**
 * Walks every page of a cursored R2DBC query, forward and backward, and checks that each row is returned exactly once.
 */
class H2R2dbcCursorPagingWalkSpec extends Specification implements H2TestPropertyProvider {

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(getProperties())

    @Shared
    CursorRowRepository repository = ctx.getBean(CursorRowRepository)

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    @Override
    Map<String, String> getProperties() {
        return H2TestPropertyProvider.super.getProperties() + [
            'r2dbc.datasources.default.url': 'r2dbc:h2:mem:///cursorPagingWalk;DB_CLOSE_DELAY=10'
        ]
    }

    def cleanup() {
        repository.deleteAll().block()
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

    static final List<String> PATHS = ["SQL", "criteria"]

    private Map<String, Function<CursoredPageable, CursoredPage<CursorRow>>> finders() {
        return [
            "SQL"     : { CursoredPageable p -> repository.findBySeqGreaterThan(-1, p).block() } as Function,
            "criteria": { CursoredPageable p -> repository.findAllCursored(PredicateSpecification.ALL, p).block() } as Function
        ]
    }

    private List<Long> save(List<String> names) {
        def rows = names.withIndex().collect { String name, int seq -> new CursorRow(name: name, seq: seq) }
        return repository.saveAll(rows).collectList().block()*.id
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

@R2dbcRepository(dialect = Dialect.H2)
interface CursorRowRepository extends ReactorCrudRepository<CursorRow, Long>, ReactorJpaSpecificationExecutor<CursorRow> {

    Mono<CursoredPage<CursorRow>> findBySeqGreaterThan(int seq, CursoredPageable pageable)

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
}

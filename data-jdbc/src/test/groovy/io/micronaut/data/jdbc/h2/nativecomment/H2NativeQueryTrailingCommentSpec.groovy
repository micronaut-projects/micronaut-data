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
package io.micronaut.data.jdbc.h2.nativecomment

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.CursoredPage
import io.micronaut.data.model.CursoredPageable
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * A native query that ends in a line comment: the runtime paging must not become part of the comment.
 */
class H2NativeQueryTrailingCommentSpec extends Specification implements H2TestPropertyProvider {

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(getProperties())

    @Shared
    CommentedItemRepository repository = ctx.getBean(CommentedItemRepository)

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    @Override
    Map<String, String> getProperties() {
        return H2TestPropertyProvider.super.getProperties() + [
            'datasources.default.url': 'jdbc:h2:mem:nativeTrailingComment;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE'
        ]
    }

    def setup() {
        repository.saveAll([new CommentedItem(name: "b"), new CommentedItem(name: "a"), new CommentedItem(name: "c")])
    }

    def cleanup() {
        repository.deleteAll()
    }

    void "a pageable with a sort is applied"() {
        when:
            Page<CommentedItem> page = repository.findPage(Pageable.from(0, 2, Sort.of(Sort.Order.desc("name"))))
        then:
            page.content*.name == ["c", "b"]
            page.totalSize == 3
    }

    void "a sort is applied"() {
        expect:
            repository.findSorted(Sort.of(Sort.Order.asc("name")))*.name == ["a", "b", "c"]
    }

    void "a cursored pageable pages through every row"() {
        when:
            CursoredPage<CommentedItem> first = repository.findCursored(CursoredPageable.from(2, Sort.of(Sort.Order.asc("name"))))
            CursoredPage<CommentedItem> second = repository.findCursored(first.nextPageable())
        then:
            first.content*.name == ["a", "b"]
            second.content*.name == ["c"]
    }
}

@JdbcRepository(dialect = Dialect.H2)
interface CommentedItemRepository extends CrudRepository<CommentedItem, Long> {

    @Query(value = "select * from commented_item commented_item_ -- all items",
        countQuery = "select count(*) from commented_item")
    Page<CommentedItem> findPage(Pageable pageable)

    @Query("select * from commented_item commented_item_ where commented_item_.name <> 'x' -- all items")
    List<CommentedItem> findSorted(Sort sort)

    @Query(value = "SELECT * FROM commented_item commented_item_ WHERE commented_item_.name <> 'x' -- all items",
        countQuery = "SELECT count(*) FROM commented_item")
    CursoredPage<CommentedItem> findCursored(CursoredPageable pageable)
}

@MappedEntity
class CommentedItem {
    @Id
    @GeneratedValue
    Long id

    String name
}

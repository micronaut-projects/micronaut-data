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
package io.micronaut.data.runtime.operations.internal.sql

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.model.Limit
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.QueryParameterBinding
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.runtime.convert.PostgresTsQueryConverter
import io.micronaut.data.runtime.mapper.sql.MappedBook
import spock.lang.Specification

class TextSearchScoreOrderSpec extends Specification {

    private static final String QUERY = 'SELECT mapped_book_."id",mapped_book_."title",mapped_book_."pages",ts_rank_cd(to_tsvector(\'english\',mapped_book_."title"),to_tsquery(\'english\',?)) AS mn_score FROM "mapped_book" mapped_book_ WHERE (to_tsvector(\'english\',mapped_book_."title") @@ to_tsquery(\'english\',?))'

    void "an unsorted pageable orders a text search by its score"() {
        when:
        def query = preparedQuery(true)
        query.attachPageable(pageable, Limit.of(pageable.size, pageable.offset), pageable.sort)

        then:
        query.query == QUERY + suffix

        where:
        pageable                                              | suffix
        Pageable.from(0, 10)                                  | ' ORDER BY mn_score DESC LIMIT 10'
        Pageable.from(2, 10)                                  | ' ORDER BY mn_score DESC LIMIT 10 OFFSET 20'
        Pageable.unpaged()                                    | ' ORDER BY mn_score DESC'
        Pageable.from(1, 5, Sort.of(Sort.Order.asc("title"))) | ' ORDER BY mapped_book_."title" ASC LIMIT 5 OFFSET 5'
        Pageable.from(Sort.of(Sort.Order.desc("pages")))      | ' ORDER BY mapped_book_."pages" DESC'
    }

    void "a query that is not ordered by score keeps an unsorted pageable unordered"() {
        when:
        def query = preparedQuery(false)
        query.attachPageable(pageable, Limit.of(pageable.size, pageable.offset), pageable.sort)

        then:
        query.query == QUERY + suffix

        where:
        pageable             | suffix
        Pageable.from(0, 10) | ' LIMIT 10'
        Pageable.unpaged()   | ''
    }

    void "a text search is recognised by a parameter bound through a text search converter"() {
        given:
        def text = Stub(QueryParameterBinding) {
            getParameterConverterClass() >> PostgresTsQueryConverter.Any
        }
        def plain = Stub(QueryParameterBinding) {
            getParameterConverterClass() >> null
        }

        expect:
        SqlStoredQuery.hasTextSearchBinding([plain, text])
        !SqlStoredQuery.hasTextSearchBinding([plain])
        !SqlStoredQuery.hasTextSearchBinding([])
        preparedQuery(true).isTextSearch()
        !preparedQuery(false).isTextSearch()
    }

    private DefaultSqlPreparedQuery<MappedBook, MappedBook> preparedQuery(boolean textSearch) {
        def entity = new RuntimePersistentEntity<MappedBook>(MappedBook)
        SqlStoredQuery<MappedBook, MappedBook> storedQuery = Stub(SqlStoredQuery) {
            getQuery() >> QUERY
            getExpandableQueryParts() >> ([QUERY] as String[])
            getQueryBindings() >> []
            getDialect() >> Dialect.POSTGRES
            getQueryBuilder() >> new SqlQueryBuilder(Dialect.POSTGRES)
            getPersistentEntity() >> entity
            getRootEntity() >> MappedBook
            getAnnotationMetadata() >> AnnotationMetadata.EMPTY_METADATA
            isScoreOrderedWhenUnsorted() >> textSearch
            isTextSearch() >> textSearch
        }
        return new DefaultSqlPreparedQuery<>(storedQuery)
    }
}

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
package io.micronaut.data.nitrite.generated

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.Query
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.nitrite.runtime.query.GeneratedQueryParser
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.dizitart.no2.filters.Filter
import spock.lang.Specification

/**
 * Exercises the SQL-shaped query path described on {@link GeneratedQueryBookRepository}.
 *
 * <p>Every fixture holds at least three documents on purpose. A filter that degrades to
 * "match everything" is indistinguishable from a correct one against a single-document
 * collection, which is exactly how this defect survived a green suite.
 */
@MicronautTest(transactional = false)
class GeneratedQueryFallbackSpec extends Specification {

    @Inject
    GeneratedQueryBookRepository bookRepository

    @Inject
    GeneratedQueryAuthorRepository authorRepository

    @Inject
    ApplicationContext applicationContext

    GeneratedQueryAuthor king
    GeneratedQueryAuthor tolkien

    def setup() {
        bookRepository.deleteAll()
        authorRepository.deleteAll()
        king = authorRepository.save(new GeneratedQueryAuthor("Stephen King"))
        tolkien = authorRepository.save(new GeneratedQueryAuthor("J.R.R. Tolkien"))
        bookRepository.saveAll([
            new GeneratedQueryBook("The Stand", 1200, "horror", king,
                new GeneratedQueryBook.Edition("uncut")),
            new GeneratedQueryBook("The Shining", 450, "horror", king,
                new GeneratedQueryBook.Edition("first")),
            new GeneratedQueryBook("The Hobbit", 310, "fantasy", tolkien,
                new GeneratedQueryBook.Edition("pocket")),
            new GeneratedQueryBook("Unfiled Draft", 90, null, null)
        ])
    }

    void "the source set really does compile to SQL-shaped queries"() {
        given: "the query string the annotation processor baked into the repository"
        def definition = applicationContext.getBeanDefinition(GeneratedQueryBookRepository)
        def method = definition.getRequiredMethod('findByTitle', String)
        def query = method.stringValue(Query).orElse(null)

        expect: "not a Nitrite JSON filter - if this fails the suite has stopped testing the fallback"
        query != null
        !query.trim().startsWith('{')
        query.toUpperCase().contains(' WHERE ')
    }

    void "equality predicate does not degrade to match-all"() {
        expect:
        bookRepository.findByTitle("The Shining").get().pages == 450
        bookRepository.findByTitle("Nothing Like This").isEmpty()
    }

    void "findById filters on the identity rather than returning the collection"() {
        given:
        def shining = bookRepository.findByTitle("The Shining").get()

        expect:
        bookRepository.findById(shining.id).get().title == "The Shining"
        bookRepository.count() == 4
    }

    void "ordering comparison"() {
        expect:
        bookRepository.findByPagesGreaterThan(400)*.title.toSorted() == ["The Shining", "The Stand"]
    }

    void "range comparison keeps its bounding AND"() {
        expect:
        bookRepository.findByPagesBetween(300, 500)*.title.toSorted() == ["The Hobbit", "The Shining"]
    }

    void "membership predicate"() {
        expect:
        bookRepository.findByTitleIn(["The Hobbit", "The Stand", "Absent"])*.title.toSorted() ==
            ["The Hobbit", "The Stand"]
    }

    void "pattern predicate"() {
        expect:
        bookRepository.findByTitleLike("The S%")*.title.toSorted() == ["The Shining", "The Stand"]
    }

    void "null tests"() {
        expect:
        bookRepository.findByGenreIsNull()*.title == ["Unfiled Draft"]
        bookRepository.findByGenreIsNotNull().size() == 3
    }

    void "conjunction and disjunction"() {
        expect:
        bookRepository.findByTitleAndPages("The Hobbit", 310)*.title == ["The Hobbit"]
        bookRepository.findByTitleAndPages("The Hobbit", 999).isEmpty()
        bookRepository.findByTitleOrPages("The Hobbit", 1200)*.title.toSorted() == ["The Hobbit", "The Stand"]
    }

    void "negated equality keeps the same document-store semantics as the JSON path"() {
        expect: "an unset field is not equal to the excluded value, as it is for a Nitrite JSON \$ne"
        bookRepository.findByGenreNotEquals("horror")*.title.toSorted() == ["The Hobbit", "Unfiled Draft"]
    }

    void "dotted association reference resolves to the persisted path"() {
        expect:
        bookRepository.findByAuthorId(tolkien.id)*.title == ["The Hobbit"]
        bookRepository.findByAuthorId(king.id).size() == 2
    }

    void "trailing ORDER BY is applied rather than parsed into the predicate"() {
        expect:
        bookRepository.findByPagesGreaterThanOrderByTitle(400)*.title == ["The Shining", "The Stand"]
    }

    void "trailing ORDER BY over a dotted path resolves each segment, mapped names included"() {
        expect: "sorted by edition.label, which is persisted as edition.label_text"
        bookRepository.findByPagesGreaterThanOrderByEditionLabel(100)*.edition*.label ==
            ["first", "pocket", "uncut"]
    }

    void "count projection over a predicate"() {
        expect:
        bookRepository.countByPagesGreaterThan(400) == 2
        bookRepository.countByPagesGreaterThan(100000) == 0
    }

    void "existence check over a predicate"() {
        expect:
        bookRepository.existsByTitle("The Hobbit")
        !bookRepository.existsByTitle("Absent")
    }

    void "delete by predicate removes only the matching documents"() {
        when:
        def deleted = bookRepository.deleteByGenre("horror")

        then:
        deleted == 2
        bookRepository.count() == 2
        bookRepository.findAll()*.title.toSorted() == ["The Hobbit", "Unfiled Draft"]
    }

    void "negated predicates"() {
        expect:
        bookRepository.findByTitleNotIn(["The Stand", "The Shining"])*.title.toSorted() == ["The Hobbit", "Unfiled Draft"]
        bookRepository.findByTitleNotLike("The S%")*.title.toSorted() == ["The Hobbit", "Unfiled Draft"]
        bookRepository.findByPagesNotEquals(1200)*.title.toSorted() == ["The Hobbit", "The Shining", "Unfiled Draft"]
    }

    void "the remaining ordering comparisons"() {
        expect:
        bookRepository.findByPagesLessThan(310)*.title == ["Unfiled Draft"]
        bookRepository.findByPagesLessThanEquals(310)*.title.toSorted() == ["The Hobbit", "Unfiled Draft"]
        bookRepository.findByPagesGreaterThanEquals(450)*.title.toSorted() == ["The Shining", "The Stand"]
    }

    void "a DTO projection carries the projected columns rather than the whole document"() {
        when:
        def single = bookRepository.queryByTitle("The Shining")

        then:
        single.title() == "The Shining"
        single.pages() == 450

        when:
        def many = bookRepository.searchByTitle("The Hobbit")

        then:
        many*.title() == ["The Hobbit"]
        many*.pages() == [310]
    }

    void "a paged read applies the page bounds and the total count"() {
        when: "three of the four books have more than 100 pages"
        def page = bookRepository.findByPagesGreaterThan(100, Pageable.from(0, 2))

        then:
        page.content.size() == 2
        page.totalSize == 3
        page.totalPages == 2
    }

    void "the parser accepts every SQL literal form"() {
        given:
        def entity = applicationContext.getBean(RuntimeEntityRegistry).getEntity(GeneratedQueryBook)
        def parser = newParser { ent, path, op -> Filter.ALL }

        expect: "each literal parses to a filter rather than being rejected"
        parser.parseWhere(select("book_.title = NULL"), entity, NO_PARAMS) != null
        parser.parseWhere(select("book_.genre = TRUE"), entity, NO_PARAMS) != null
        parser.parseWhere(select("book_.genre = FALSE"), entity, NO_PARAMS) != null
        parser.parseWhere(select("book_.pages > 100.5"), entity, NO_PARAMS) != null
        parser.parseWhere(select("book_.pages > 100"), entity, NO_PARAMS) != null
    }

    void "the parser strips redundant parentheses around a predicate"() {
        given:
        def entity = applicationContext.getBean(RuntimeEntityRegistry).getEntity(GeneratedQueryBook)
        def parser = newParser { ent, path, op -> Filter.ALL }

        expect:
        parser.parseWhere(select("((book_.pages = 1) AND (book_.title = 'a'))"), entity, NO_PARAMS) != null
        parser.parseWhere(select("(book_.pages = 1) OR (book_.title = 'a')"), entity, NO_PARAMS) != null
    }

    void "parseSet reads one assignment per SET clause entry"() {
        given:
        def entity = applicationContext.getBean(RuntimeEntityRegistry).getEntity(GeneratedQueryBook)
        def parser = newParser { ent, path, op -> Filter.ALL }

        expect: "the trailing WHERE is not mistaken for a further assignment"
        parser.parseSet("UPDATE generated_query_books SET book_.pages = :p1 WHERE book_.title = :p2",
            entity, [100, "The Hobbit"] as Object[]).values() as List == [100]
        parser.parseSet("UPDATE generated_query_books SET book_.pages = :p1",
            entity, [100] as Object[]).values() as List == [100]

        and: "a statement with no SET clause yields no assignments"
        parser.parseSet("UPDATE generated_query_books WHERE book_.pages = 1", entity, NO_PARAMS).isEmpty()
    }

    void "parseOrderBy returns the trailing sort, or null when there is none"() {
        expect:
        GeneratedQueryParser.parseOrderBy("SELECT book_ FROM b ORDER BY title DESC").orderBy*.property == ["title"]
        !GeneratedQueryParser.parseOrderBy("SELECT book_ FROM b ORDER BY title DESC").orderBy.first().ascending

        and: "an empty list of terms is not a sort"
        GeneratedQueryParser.parseOrderBy("SELECT book_ FROM b ORDER BY ") == null

        and: "blank terms are skipped rather than resolved as properties"
        GeneratedQueryParser.parseOrderBy("SELECT book_ FROM b ORDER BY , title").orderBy*.property == ["title"]
    }

    void "an unparseable SET assignment is rejected"() {
        given:
        def entity = applicationContext.getBean(RuntimeEntityRegistry).getEntity(GeneratedQueryBook)
        def parser = newParser { ent, path, op -> Filter.ALL }

        when:
        parser.parseSet("UPDATE generated_query_books SET book_ WHERE book_.pages = 1", entity, NO_PARAMS)

        then:
        thrown(UnsupportedOperationException)
    }

    void "an unparseable comparison is rejected"() {
        given:
        def entity = applicationContext.getBean(RuntimeEntityRegistry).getEntity(GeneratedQueryBook)
        def parser = newParser { ent, path, op -> Filter.ALL }

        when:
        parser.parseWhere(select("book_.pages ?? 1"), entity, NO_PARAMS)

        then:
        thrown(UnsupportedOperationException)
    }

    void "a leaf the filter factory cannot build is rejected"() {
        given:
        def entity = applicationContext.getBean(RuntimeEntityRegistry).getEntity(GeneratedQueryBook)
        def parser = newParser { ent, path, op -> null }

        when:
        parser.parseWhere(select("book_.pages = 1"), entity, NO_PARAMS)

        then:
        thrown(UnsupportedOperationException)
    }

    void "a malformed parameter reference is rejected"() {
        given:
        def entity = applicationContext.getBean(RuntimeEntityRegistry).getEntity(GeneratedQueryBook)
        def parser = newParser { ent, path, op -> Filter.ALL }

        when:
        parser.parseWhere(select("book_.pages = :px"), entity, NO_PARAMS)

        then:
        thrown(UnsupportedOperationException)
    }

    void "a parameter reference past the end of the argument list is rejected"() {
        given:
        def entity = applicationContext.getBean(RuntimeEntityRegistry).getEntity(GeneratedQueryBook)
        def parser = newParser { ent, path, op -> Filter.ALL }

        when:
        parser.parseWhere(select("book_.pages = :p2"), entity, [1] as Object[])

        then:
        thrown(IllegalStateException)
    }

    private static final Object[] NO_PARAMS = new Object[0]

    private static String select(String predicate) {
        "SELECT book_ FROM generated_query_books book_ WHERE $predicate"
    }

    private static GeneratedQueryParser newParser(Closure<Filter> leafFilterFactory) {
        new GeneratedQueryParser(leafFilterFactory as GeneratedQueryParser.LeafFilterFactory)
    }
}

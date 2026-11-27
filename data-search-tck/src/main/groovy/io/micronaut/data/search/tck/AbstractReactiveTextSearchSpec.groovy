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
package io.micronaut.data.search.tck

import io.micronaut.context.ApplicationContext
import io.micronaut.data.search.tck.entities.Book
import io.micronaut.data.search.tck.repositories.ReactorBookSearchRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * The reactive full-text search test kit: a backend passes it by implementing
 * {@link ReactorBookSearchRepository}.
 */
abstract class AbstractReactiveTextSearchSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    abstract ReactorBookSearchRepository getBookSearchRepository()

    void setup() {
        bookSearchRepository.deleteAll().block()
        bookSearchRepository.saveAll([
                new Book("The Rust Programming Language", "Learn systems programming with Rust", "Klabnik", 560),
                new Book("Programming Kotlin", "Kotlin for the JVM and beyond", "Subramaniam", 460),
                new Book("Java Concurrency in Practice", "Threads and locks for the Java programmer", "Goetz", 400),
                new Book("Cooking with Coffee", "Recipes and desserts made with coffee beans", "Bean", 120),
        ]).collectList().block()
    }

    void cleanup() {
        bookSearchRepository.deleteAll().block()
    }

    void "a reactive search returns the hits ordered by relevance"() {
        when:
        def results = bookSearchRepository.searchBySearch("programming").block()

        then:
        results.results().size() == 2
        results.results()*.entity()*.title as Set == ["The Rust Programming Language", "Programming Kotlin"] as Set
        results.results().every { it.score().value() > 0 }
        results.results()[0].score().value() >= results.results()[1].score().value()
        bookSearchRepository.searchBySearch("nothingmatchesthis").block().results().isEmpty()
    }

    void "reactive finders and counts use the search keyword"() {
        expect:
        bookSearchRepository.findByTitleSearch("java").collectList().block()*.title == ["Java Concurrency in Practice"]
        bookSearchRepository.countBySearch("programming").block() == 2L
        bookSearchRepository.countBySearch("nothingmatchesthis").block() == 0L
    }

    void "reactive CRUD round trip"() {
        when:
        def saved = bookSearchRepository.save(new Book("Baking Bread", "Sourdough for the home baker", "Dough", 220)).block()

        then:
        saved.id != null
        bookSearchRepository.findById(saved.id).block().title == "Baking Bread"
        bookSearchRepository.count().block() == 5L

        when:
        saved.title = "Baking Sourdough"
        bookSearchRepository.update(saved).block()

        then:
        bookSearchRepository.findById(saved.id).block().title == "Baking Sourdough"

        when:
        bookSearchRepository.deleteById(saved.id).block()

        then:
        bookSearchRepository.findById(saved.id).blockOptional().isEmpty()
        bookSearchRepository.count().block() == 4L
    }
}

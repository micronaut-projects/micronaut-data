/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.data.runtime.intercept.criteria.async

import io.micronaut.data.operations.reactive.ReactiveCriteriaRepositoryOperations
import io.micronaut.data.runtime.operations.ReactivePageIdCriteriaRepositoryOperations
import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaDelete
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.CriteriaUpdate
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

class ReactiveAsyncCriteriaRepositoryOperationsSpec extends Specification {

    def reactiveOperations = Mock(ReactiveCriteriaRepositoryOperations)
    def operations = new ReactiveAsyncCriteriaRepositoryOperations(reactiveOperations)

    void "delegates criteria builder lookup"() {
        given:
            def criteriaBuilder = Mock(CriteriaBuilder)

        when:
            def result = operations.getCriteriaBuilder()

        then:
            1 * reactiveOperations.getCriteriaBuilder() >> criteriaBuilder
            result.is(criteriaBuilder)
    }

    void "adapts exists publisher to completion stage"() {
        given:
            def query = Mock(CriteriaQuery)

        when:
            def result = operations.exists(query).toCompletableFuture().join()

        then:
            1 * reactiveOperations.exists(query) >> Mono.just(true)
            result
    }

    void "adapts find one publisher to completion stage"() {
        given:
            def query = Mock(CriteriaQuery)

        when:
            def result = operations.findOne(query).toCompletableFuture().join()

        then:
            1 * reactiveOperations.findOne(query) >> Mono.just("alpha")
            result == "alpha"
    }

    void "adapts find all publisher to list completion stage"() {
        given:
            def query = Mock(CriteriaQuery)

        when:
            def result = operations.findAll(query).toCompletableFuture().join()

        then:
            1 * reactiveOperations.findAll(query) >> Flux.just("alpha", "beta")
            result == ["alpha", "beta"]
    }

    void "adapts paged find all publisher to list completion stage"() {
        given:
            def query = Mock(CriteriaQuery)

        when:
            def result = operations.findAll(query, 10, 20).toCompletableFuture().join()

        then:
            1 * reactiveOperations.findAll(query, 10, 20) >> Flux.just("alpha", "beta")
            result == ["alpha", "beta"]
    }

    void "delegates page ID query to reactive page ID operations"() {
        given:
            def pageIdOperations = Mock(ReactivePageIdCriteriaOperationsWithCriteria)
            def operations = new ReactiveAsyncCriteriaRepositoryOperations(pageIdOperations)
            def query = Mock(CriteriaQuery)

        when:
            def result = operations.findPageIds(query, 10, 20).toCompletableFuture().join()

        then:
            1 * pageIdOperations.findPageIds(query, 10, 20) >> Flux.just("alpha", "beta")
            result == ["alpha", "beta"]
    }

    void "falls back to paged find all when reactive operations do not support page ID queries"() {
        given:
            def query = Mock(CriteriaQuery)

        when:
            def result = operations.findPageIds(query, 10, 20).toCompletableFuture().join()

        then:
            1 * reactiveOperations.findAll(query, 10, 20) >> Flux.just("alpha", "beta")
            result == ["alpha", "beta"]
    }

    void "adapts update publisher to completion stage"() {
        given:
            def query = Mock(CriteriaUpdate)

        when:
            def result = operations.updateAll(query).toCompletableFuture().join()

        then:
            1 * reactiveOperations.updateAll(query) >> Mono.just(3)
            result == 3
    }

    void "adapts delete publisher to completion stage"() {
        given:
            def query = Mock(CriteriaDelete)

        when:
            def result = operations.deleteAll(query).toCompletableFuture().join()

        then:
            1 * reactiveOperations.deleteAll(query) >> Mono.just(4)
            result == 4
    }

    private interface ReactivePageIdCriteriaOperationsWithCriteria extends ReactiveCriteriaRepositoryOperations, ReactivePageIdCriteriaRepositoryOperations {
    }
}

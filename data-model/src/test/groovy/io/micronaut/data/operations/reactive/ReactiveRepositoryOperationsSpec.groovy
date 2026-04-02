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
package io.micronaut.data.operations.reactive

import io.micronaut.core.convert.ConversionService
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.model.Page
import io.micronaut.data.model.runtime.DeleteBatchOperation
import io.micronaut.data.model.runtime.DeleteOperation
import io.micronaut.data.model.runtime.InsertBatchOperation
import io.micronaut.data.model.runtime.InsertOperation
import io.micronaut.data.model.runtime.PagedQuery
import io.micronaut.data.model.runtime.PreparedQuery
import io.micronaut.data.model.runtime.UpdateBatchOperation
import io.micronaut.data.model.runtime.UpdateOperation
import org.reactivestreams.Publisher
import spock.lang.Specification

class ReactiveRepositoryOperationsSpec extends Specification {

    void "deleteReturning default throws DataAccessException when unsupported"() {
        given:
        ReactiveRepositoryOperations operations = unsupportedOperations()

        when:
        operations.deleteReturning(null)

        then:
        def e = thrown(DataAccessException)
        e.message.contains("doesn't support method 'deleteReturning'")
    }

    void "deleteAllReturning default throws DataAccessException when unsupported"() {
        given:
        ReactiveRepositoryOperations operations = unsupportedOperations()

        when:
        operations.deleteAllReturning(null)

        then:
        def e = thrown(DataAccessException)
        e.message.contains("doesn't support method 'deleteAllReturning'")
    }

    private static ReactiveRepositoryOperations unsupportedOperations() {
        return new ReactiveRepositoryOperations() {
            @Override
            ConversionService getConversionService() {
                return ConversionService.SHARED
            }

            @Override
            <T> Publisher<T> findOne(Class<T> type, Object id) {
                return null
            }

            @Override
            <T> Publisher<Boolean> exists(PreparedQuery<T, Boolean> preparedQuery) {
                return null
            }

            @Override
            <T, R> Publisher<R> findOne(PreparedQuery<T, R> preparedQuery) {
                return null
            }

            @Override
            <T> Publisher<T> findOptional(Class<T> type, Object id) {
                return null
            }

            @Override
            <T, R> Publisher<R> findOptional(PreparedQuery<T, R> preparedQuery) {
                return null
            }

            @Override
            <T> Publisher<T> findAll(PagedQuery<T> pagedQuery) {
                return null
            }

            @Override
            <T> Publisher<Long> count(PagedQuery<T> pagedQuery) {
                return null
            }

            @Override
            <T, R> Publisher<R> findAll(PreparedQuery<T, R> preparedQuery) {
                return null
            }

            @Override
            <T> Publisher<T> persist(InsertOperation<T> operation) {
                return null
            }

            @Override
            <T> Publisher<T> update(UpdateOperation<T> operation) {
                return null
            }

            @Override
            <T> Publisher<T> updateAll(UpdateBatchOperation<T> operation) {
                return null
            }

            @Override
            <T> Publisher<T> persistAll(InsertBatchOperation<T> operation) {
                return null
            }

            @Override
            Publisher<Number> executeUpdate(PreparedQuery<?, Number> preparedQuery) {
                return null
            }

            @Override
            <T> Publisher<Number> delete(DeleteOperation<T> operation) {
                return null
            }

            @Override
            <T> Publisher<Number> deleteAll(DeleteBatchOperation<T> operation) {
                return null
            }

            @Override
            <R> Publisher<Page<R>> findPage(PagedQuery<R> pagedQuery) {
                return null
            }
        }
    }
}

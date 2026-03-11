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
package io.micronaut.data.jdbc.h2

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.OptimisticLockConflict
import io.micronaut.data.annotation.Version
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.exceptions.OptimisticLockExceptionHandler
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.GenericRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicInteger

@MicronautTest
@H2DBProperties
class H2OptimisticLockConflictPolicySpec extends Specification {

    @Inject
    H2OptimisticLockRepository repository

    @Inject
    H2OptimisticLockNoFindByIdRepository noFindByIdRepository

    @Inject
    TestOptimisticLockExceptionHandler handler

    void "test jdbc optimistic lock conflict policy fail-fast delegate and reload-retry"() {
        given:
        def entity = repository.save(new H2OptimisticLockEntity(name: "initial"))
        def staleVersion = entity.version

        when:
        repository.update(entity.id, entity.version, "changed")
        def current = repository.findById(entity.id).orElseThrow()

        then:
        current.version > staleVersion

        when:
        repository.update(entity.id, staleVersion, "should-fail")

        then:
        thrown(OptimisticLockException)
        handler.callCount.get() == 0

        when:
        repository.updateWithDelegate(entity.id, staleVersion, "stale-value")
        def reloaded = repository.findById(entity.id).orElseThrow()

        then:
        handler.callCount.get() == 1
        reloaded.name == "changed"
        reloaded.version == current.version

        when:
        repository.updateWithReloadAndRetry(entity.id, staleVersion, "should-retry")
        def retried = repository.findById(entity.id).orElseThrow()

        then:
        retried.name == "should-retry"
        retried.version > reloaded.version

        when:
        def entityUpdate = repository.findById(entity.id).orElseThrow()
        def staleEntityVersion = entityUpdate.version
        repository.update(entity.id, staleEntityVersion, "concurrent-change")
        def afterConcurrent = repository.findById(entity.id).orElseThrow()
        entityUpdate.name = "entity-merged"
        repository.update(entityUpdate)
        def afterEntityRetry = repository.findById(entity.id).orElseThrow()

        then:
        afterConcurrent.version > staleEntityVersion
        afterEntityRetry.name == "entity-merged"
        afterEntityRetry.version > afterConcurrent.version

        when:
        def asyncEntity = repository.findById(entity.id).orElseThrow()
        def asyncStaleVersion = asyncEntity.version
        repository.update(entity.id, asyncStaleVersion, "async-concurrent")
        asyncEntity.name = "async-merged"
        repository.updateAsyncWithReloadAndRetry(asyncEntity).toCompletableFuture().join()
        def asyncReloaded = repository.findById(entity.id).orElseThrow()

        then:
        asyncReloaded.name == "async-merged"
    }

    void "test reload and retry fails clearly without findById"() {
        given:
        def entity = repository.save(new H2OptimisticLockEntity(name: "initial"))
        def staleVersion = entity.version
        repository.update(entity.id, entity.version, "changed")

        when:
        noFindByIdRepository.updateWithReloadAndRetry(entity.id, staleVersion, "retry")

        then:
        def ex = thrown(IllegalStateException)
        ex.message.contains("RELOAD_AND_RETRY policy requires findById(ID) method on repository.")
    }

    @Singleton
    static final class TestOptimisticLockExceptionHandler implements OptimisticLockExceptionHandler {
        final AtomicInteger callCount = new AtomicInteger()

        @Override
        Object handle(OptimisticLockException exception, io.micronaut.aop.MethodInvocationContext<?, ?> context) {
            callCount.incrementAndGet()
            return null
        }
    }
}

@JdbcRepository(dialect = io.micronaut.data.model.query.builder.sql.Dialect.H2)
interface H2OptimisticLockRepository extends CrudRepository<H2OptimisticLockEntity, Long> {

    void update(@Id Long id, @Version Long version, String name)

    @OptimisticLockConflict(OptimisticLockConflict.Policy.DELEGATE)
    void updateWithDelegate(@Id Long id, @Version Long version, String name)

    @OptimisticLockConflict(value = OptimisticLockConflict.Policy.RELOAD_AND_RETRY, maxRetries = 2)
    void updateWithReloadAndRetry(@Id Long id, @Version Long version, String name)

    @Override
    @OptimisticLockConflict(value = OptimisticLockConflict.Policy.RELOAD_AND_RETRY, maxRetries = 2)
    H2OptimisticLockEntity update(H2OptimisticLockEntity entity)

    @OptimisticLockConflict(value = OptimisticLockConflict.Policy.RELOAD_AND_RETRY, maxRetries = 2)
    CompletionStage<H2OptimisticLockEntity> updateAsyncWithReloadAndRetry(H2OptimisticLockEntity entity)
}

@JdbcRepository(dialect = io.micronaut.data.model.query.builder.sql.Dialect.H2)
interface H2OptimisticLockNoFindByIdRepository extends GenericRepository<H2OptimisticLockEntity, Long> {

    @OptimisticLockConflict(value = OptimisticLockConflict.Policy.RELOAD_AND_RETRY, maxRetries = 2)
    void updateWithReloadAndRetry(@Id Long id, @Version Long version, String name)
}

@MappedEntity("h2_optimistic_lock_entity")
class H2OptimisticLockEntity {
    @Id
    @GeneratedValue
    Long id

    String name

    @Version
    Long version
}

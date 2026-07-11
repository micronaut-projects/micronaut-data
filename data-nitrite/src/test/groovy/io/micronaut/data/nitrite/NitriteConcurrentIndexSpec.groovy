package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.UniqueIndexedEntity
import io.micronaut.data.nitrite.repository.UniqueIndexedEntityRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Regression test for nitrite 4.4.1: concurrent writes to a unique index and a full-text index
 * could race and throw a spurious ConcurrentModificationException or a false unique-constraint
 * violation. Fires many concurrent inserts, each with a distinct unique-indexed value and a
 * full-text-indexed field, and asserts they all succeed without corrupting either index.
 */
@MicronautTest(transactional = false)
class NitriteConcurrentIndexSpec extends Specification {

    @Inject
    UniqueIndexedEntityRepository repo

    def setup() {
        repo.deleteAll()
    }

    void "concurrent inserts on unique and full-text indexes do not race or corrupt the indexes"() {
        given:
            int count = 50
            ExecutorService pool = Executors.newFixedThreadPool(16)
            CountDownLatch ready = new CountDownLatch(count)
            CountDownLatch start = new CountDownLatch(1)
            def errors = new CopyOnWriteArrayList<Throwable>()

        when:
            (0..<count).each { i ->
                pool.submit({
                    ready.countDown()
                    start.await()
                    try {
                        repo.save(new UniqueIndexedEntity("code-$i", "description text number $i"))
                    } catch (Throwable t) {
                        errors << t
                    }
                } as Runnable)
            }
            ready.await(5, TimeUnit.SECONDS)
            start.countDown()
            pool.shutdown()
            pool.awaitTermination(30, TimeUnit.SECONDS)

        then: "no spurious exceptions and every distinct-code insert succeeded"
            errors.isEmpty()
            repo.count() == count
            repo.findAll()*.code.toSet().size() == count
    }
}

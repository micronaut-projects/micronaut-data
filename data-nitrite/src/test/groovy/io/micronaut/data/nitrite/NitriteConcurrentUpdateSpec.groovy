package io.micronaut.data.nitrite

import io.micronaut.data.nitrite.model.Event
import io.micronaut.data.nitrite.repository.EventRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Nitrite has no arithmetic update operator, so an increment is read, computed and written back by
 * this module. That sequence has to be atomic per collection, otherwise two threads read the same
 * value and one increment is lost.
 */
@MicronautTest(transactional = false)
class NitriteConcurrentUpdateSpec extends Specification {

    private static final int THREADS = 8
    private static final int INCREMENTS_PER_THREAD = 50

    @Inject
    EventRepository eventRepository

    def setup() {
        eventRepository.deleteAll()
    }

    void "concurrent increments of the same document are not lost"() {
        given:
        def event = new Event()
        event.type = "counter"
        event.priority = 0
        eventRepository.save(event)

        def executor = Executors.newFixedThreadPool(THREADS)
        def start = new CountDownLatch(1)
        def done = new CountDownLatch(THREADS)
        def failures = Collections.synchronizedList([])

        when:
        THREADS.times {
            executor.submit {
                try {
                    start.await()
                    INCREMENTS_PER_THREAD.times {
                        eventRepository.incrementPriority("counter", 1)
                    }
                } catch (Throwable t) {
                    failures << t
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        done.await(60, TimeUnit.SECONDS)
        executor.shutdown()

        then:
        failures.isEmpty()
        eventRepository.findByType("counter").first().priority == THREADS * INCREMENTS_PER_THREAD

        cleanup:
        executor.shutdownNow()
    }
}

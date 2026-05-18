package io.micronaut.data.connection.reactive

import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.connection.ConnectionSynchronization
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicInteger

class ReactiveConnectionSynchronizationSpec extends Specification {

    static final class Tracker {
        final AtomicInteger executionComplete = new AtomicInteger()
        final AtomicInteger beforeClosed = new AtomicInteger()
        final AtomicInteger afterClosed = new AtomicInteger()
    }

    def "reactive: all registered synchronizations execute even if one throws"() {
        given:
        def tracker = new Tracker()
        def connOperations = new ReactiveConnManager()
        // new reactive connection status with a dummy connection
        def status = new DefaultReactiveConnectionStatus<>(new Object(), ConnectionDefinition.DEFAULT, connOperations, true)

        and: "a normal synchronization that tracks all callbacks"
        status.registerSynchronization(new ConnectionSynchronization() {
            @Override
            void executionComplete() {
                tracker.executionComplete.incrementAndGet()
            }

            @Override
            void beforeClosed() {
                tracker.beforeClosed.incrementAndGet()
            }

            @Override
            void afterClosed() {
                tracker.afterClosed.incrementAndGet()
            }
        })

        and: "a synchronization that throws during executionComplete"
        status.registerReactiveSynchronization(new ReactiveConnectionSynchronization() {
            @Override
            Publisher<Void> onComplete() {
                return Mono.defer {
                    throw new RuntimeException("simulated onComplete failure")
                }
            }

            @Override
            Publisher<Void> onClose() {
                return Mono.empty()
            }

            @Override
            Publisher<Void> afterClose() {
                return Mono.empty()
            }
        })

        and: "a close supplier that succeeds"
        def closeSupplier = { Mono.empty() } as java.util.function.Supplier<Publisher<Void>>

        when: "we signal completion which triggers executionComplete and finally chain"
        try {
            Mono.from(status.onComplete(closeSupplier)).block()
        } catch (Throwable ignored) {
            // expected: we only care callbacks were invoked despite failure
        }

        then: "all normal synchronization callbacks should have been invoked at least once"
        tracker.executionComplete.get() >= 1
        tracker.beforeClosed.get() >= 1
        tracker.afterClosed.get() >= 1
    }
}

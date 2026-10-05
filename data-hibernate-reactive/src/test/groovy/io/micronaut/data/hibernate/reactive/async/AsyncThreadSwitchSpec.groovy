package io.micronaut.data.hibernate.reactive.async

import io.micronaut.data.hibernate.reactive.PostgresHibernateReactiveProperties
import io.micronaut.data.tck.entities.Person
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.annotation.Transactional
import io.micronaut.scheduling.TaskExecutors
import jakarta.annotation.PreDestroy
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import spock.lang.Issue
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@MicronautTest(transactional = false, packages = "io.micronaut.data.tck.entities")
class AsyncThreadSwitchSpec extends Specification implements PostgresHibernateReactiveProperties {

    @Inject
    AsyncPersonRepo repository

    @Inject
    ThreadSwitchingService service

    @Issue("https://github.com/micronaut-projects/micronaut-data/issues/2165")
    void "test a transactional future continues after switching threads"() {
        when:
        def found = service.saveSwitchThreadAndFind("Ann").toCompletableFuture().get()

        then:
        found.name == "Ann"
        repository.findByName("Ann").get().name == "Ann"
    }

    @Issue("https://github.com/micronaut-projects/micronaut-data/issues/2165")
    void "test a transactional future commits an update made after switching threads"() {
        when:
        service.saveSwitchThreadAndUpdate("Bob", 42).toCompletableFuture().get()

        then:
        repository.findByName("Bob").get().age == 42
    }

    @Issue("https://github.com/micronaut-projects/micronaut-data/issues/2165")
    void "test a transactional future that completes on another thread commits"() {
        when:
        def saved = service.saveAndCompleteOnPlainThread("Cid").toCompletableFuture().get()

        then:
        saved.name == "Cid"
        repository.findByName("Cid").get().name == "Cid"
    }

    @Singleton
    static class ThreadSwitchingService {

        private final AsyncPersonRepo repository
        // Stands in for an HTTP client or any other API completing on its own threads; like them, it propagates the context
        private final ExecutorService otherThreads

        ThreadSwitchingService(AsyncPersonRepo repository, @Named(TaskExecutors.IO) ExecutorService otherThreads) {
            this.repository = repository
            this.otherThreads = otherThreads
        }

        // A plain executor: completes the future on a thread that is neither a Vert.x thread nor propagates the context
        private final ExecutorService plainThreads = Executors.newSingleThreadExecutor()

        @PreDestroy
        void shutdown() {
            plainThreads.shutdown()
        }

        @Transactional
        CompletionStage<Person> saveAndCompleteOnPlainThread(String name) {
            return repository.save(new Person(name: name, age: 30))
                .thenApplyAsync({ Person p -> p }, plainThreads)
        }

        @Transactional
        CompletionStage<Person> saveSwitchThreadAndFind(String name) {
            return repository.save(new Person(name: name, age: 20))
                .thenComposeAsync({ Person p -> CompletableFuture.completedFuture(p) }, otherThreads)
                .thenCompose({ Person p -> repository.findById(p.id) })
        }

        @Transactional
        CompletionStage<Integer> saveSwitchThreadAndUpdate(String name, int age) {
            return repository.save(new Person(name: name, age: 20))
                .thenComposeAsync({ Person p -> CompletableFuture.completedFuture(p) }, otherThreads)
                .thenCompose({ Person p -> repository.updateByName(name, age) })
        }
    }
}

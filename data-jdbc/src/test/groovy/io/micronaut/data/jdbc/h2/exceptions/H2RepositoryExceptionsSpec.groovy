package io.micronaut.data.jdbc.h2.exceptions

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Version
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.exceptions.DataIntegrityViolationException
import io.micronaut.data.exceptions.EmptyResultException
import io.micronaut.data.exceptions.EntityExistsException
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.repository.async.AsyncCrudRepository
import io.micronaut.data.repository.reactive.ReactorCrudRepository
import jakarta.annotation.Nullable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import reactor.core.publisher.Mono

import java.sql.SQLException
import java.util.concurrent.CompletionException

/**
 * Documents the exceptions thrown by {@link CrudRepository} methods on JDBC.
 * The behaviour asserted here is described in the "Exception Handling" guide section.
 */
class H2RepositoryExceptionsSpec extends Specification implements H2TestPropertyProvider {

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(getProperties())

    @Shared
    ExcItemRepository itemRepository = ctx.getBean(ExcItemRepository)

    @Shared
    ExcVersionedRepository versionedRepository = ctx.getBean(ExcVersionedRepository)

    @Shared
    ExcItemAsyncRepository asyncRepository = ctx.getBean(ExcItemAsyncRepository)

    @Shared
    ExcItemReactiveRepository reactiveRepository = ctx.getBean(ExcItemReactiveRepository)

    void cleanup() {
        itemRepository.deleteAll()
        versionedRepository.deleteAll()
    }

    void "insert of an existing id throws EntityExistsException wrapping the driver SQLException"() {
        given:
            itemRepository.insert(new ExcItem(id: 1L, name: "A"))

        when:
            itemRepository.insert(new ExcItem(id: 1L, name: "B"))

        then:
            def e = thrown(EntityExistsException)
            e instanceof DataAccessException
            e.cause instanceof SQLException
    }

    void "violating a NOT NULL column throws DataIntegrityViolationException"() {
        when:
            itemRepository.insert(new ExcItem(id: 2L, name: null))

        then:
            def e = thrown(DataIntegrityViolationException)
            !(e instanceof EntityExistsException)
            e.cause instanceof SQLException
    }

    void "non-nullable single result that is not found throws EmptyResultException"() {
        when:
            itemRepository.getByName("missing")

        then:
            thrown(EmptyResultException)

        expect:
            itemRepository.findByName("missing") == null
            !itemRepository.findById(123L).present
    }

    void "stale version throws OptimisticLockException on update and delete"() {
        given:
            def entity = versionedRepository.save(new ExcVersioned(name: "A"))
            entity.version = 42

        when:
            versionedRepository.update(entity)

        then:
            thrown(OptimisticLockException)

        when:
            versionedRepository.delete(entity)

        then:
            thrown(OptimisticLockException)
    }

    void "update and delete of a missing entity without a version is a silent no-op"() {
        when:
            itemRepository.update(new ExcItem(id: 999L, name: "X"))
            itemRepository.deleteById(999L)
            itemRepository.delete(new ExcItem(id: 999L, name: "X"))

        then:
            noExceptionThrown()
            itemRepository.count() == 0
    }

    void "null argument to #method is rejected with IllegalArgumentException before reaching the database"() {
        given:
            itemRepository.insert(new ExcItem(id: 7L, name: "keep"))

        when:
            call.call(itemRepository)

        then:
            def e = thrown(RuntimeException)
            e.class == IllegalArgumentException
            e.message.contains(message)
            itemRepository.count() == 1

        where:
            method          | call                               | message
            "findById"      | { r -> r.findById(null) }          | "[id]"
            "existsById"    | { r -> r.existsById(null) }        | "[id]"
            "deleteById"    | { r -> r.deleteById(null) }        | "[id]"
            "save"          | { r -> r.save(null) }              | "Entity argument [entity] of repository method [save] cannot be null"
            "insert"        | { r -> r.insert(null) }            | "Entity argument [entity] of repository method [insert] cannot be null"
            "update"        | { r -> r.update(null) }            | "Entity argument [entity] of repository method [update] cannot be null"
            "delete"        | { r -> r.delete(null) }            | "Entity argument [entity] of repository method [delete] cannot be null"
            "saveAll"       | { r -> r.saveAll(null) }           | "Entities argument [entities] of repository method [saveAll] cannot be null"
            "insertAll"     | { r -> r.insertAll(null) }         | "Entities argument [entities] of repository method [insertAll] cannot be null"
            "updateAll"     | { r -> r.updateAll(null) }         | "Entities argument [entities] of repository method [updateAll] cannot be null"
            "deleteAll"     | { r -> r.deleteAll((Iterable) null) } | "Entities argument [entities] of repository method [deleteAll] cannot be null"
    }

    void "null entity argument to async #method completes exceptionally with IllegalArgumentException"() {
        when:
            call.call(asyncRepository).toCompletableFuture().join()

        then:
            def e = thrown(CompletionException)
            e.cause instanceof IllegalArgumentException
            e.cause.message.contains("cannot be null")

        where:
            method      | call
            "save"      | { r -> r.save(null) }
            "update"    | { r -> r.update(null) }
            "delete"    | { r -> r.delete(null) }
            "saveAll"   | { r -> r.saveAll(null) }
            "deleteAll" | { r -> r.deleteAll((Iterable) null) }
    }

    void "null entity argument to reactive #method signals IllegalArgumentException"() {
        when:
            Mono.from(call.call(reactiveRepository)).block()

        then:
            def e = thrown(IllegalArgumentException)
            e.message.contains("cannot be null")

        where:
            method      | call
            "save"      | { r -> r.save(null) }
            "update"    | { r -> r.update(null) }
            "delete"    | { r -> r.delete(null) }
            "saveAll"   | { r -> r.saveAll(null) }
            "deleteAll" | { r -> r.deleteAll((Iterable) null) }
    }
}

@MappedEntity("exc_item")
class ExcItem {
    @Id
    Long id
    String name
}

@MappedEntity("exc_versioned")
class ExcVersioned {
    @Id
    @GeneratedValue
    Long id
    String name
    @Version
    Long version
}

@JdbcRepository(dialect = Dialect.H2)
interface ExcItemRepository extends CrudRepository<ExcItem, Long> {

    ExcItem getByName(String name)

    @Nullable
    ExcItem findByName(String name)
}

@JdbcRepository(dialect = Dialect.H2)
interface ExcVersionedRepository extends CrudRepository<ExcVersioned, Long> {
}

@JdbcRepository(dialect = Dialect.H2)
interface ExcItemAsyncRepository extends AsyncCrudRepository<ExcItem, Long> {
}

@JdbcRepository(dialect = Dialect.H2)
interface ExcItemReactiveRepository extends ReactorCrudRepository<ExcItem, Long> {
}

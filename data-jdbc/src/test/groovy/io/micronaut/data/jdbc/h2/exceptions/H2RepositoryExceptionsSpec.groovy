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
import jakarta.annotation.Nullable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.sql.SQLException

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

    void "null argument to #method is rejected with #expected.simpleName before reaching the database"() {
        when:
            call.call(itemRepository)

        then:
            def e = thrown(RuntimeException)
            e.class == expected

        where:
            method          | call                               | expected
            "findById"      | { r -> r.findById(null) }          | IllegalArgumentException
            "existsById"    | { r -> r.existsById(null) }        | IllegalArgumentException
            "deleteById"    | { r -> r.deleteById(null) }        | IllegalArgumentException
            "save"          | { r -> r.save(null) }              | IllegalStateException
            "insert"        | { r -> r.insert(null) }            | IllegalStateException
            "update"        | { r -> r.update(null) }            | IllegalStateException
            "delete"        | { r -> r.delete(null) }            | IllegalStateException
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

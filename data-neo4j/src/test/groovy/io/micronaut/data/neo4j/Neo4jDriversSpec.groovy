package io.micronaut.data.neo4j

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.neo4j.features.BookmarkRecorder
import io.micronaut.data.neo4j.features.OtherDriverTagRepository
import io.micronaut.data.neo4j.features.Tag
import io.micronaut.data.neo4j.features.TagRepository
import io.micronaut.data.neo4j.features.TagService
import io.micronaut.data.neo4j.operations.Neo4jReactorRepositoryOperations
import io.micronaut.data.neo4j.operations.Neo4jRepositoryOperations
import io.micronaut.data.neo4j.session.Neo4jConnectionOperations
import io.micronaut.data.neo4j.session.Neo4jSessionConfigurer
import io.micronaut.data.neo4j.transaction.Neo4jReactorTransactionOperations
import io.micronaut.data.neo4j.transaction.Neo4jTransactionOperations
import io.micronaut.data.runtime.multitenancy.SchemaTenantResolver
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Checks repositories on several drivers, bookmarks shared by the sessions of a driver and a database per tenant.
 */
class Neo4jDriversSpec extends Specification {

    void "a repository uses the driver it names"() {
        given:
        def context = ApplicationContext.run(['neo4j.uri': EmbeddedNeo4j.boltUri(), 'micronaut.data.neo4j.drivers.other.uri': EmbeddedNeo4j.boltUri()])
        def tags = context.getBean(TagRepository)
        def otherTags = context.getBean(OtherDriverTagRepository)
        tags.deleteAll()

        when: "a node is saved through the other driver, which connects to the same server"
        def saved = otherTags.save(new Tag("drivers"))

        then: "it is read through either driver"
        otherTags.findByName("drivers").get().id == saved.id
        tags.findById(saved.id).get().name == "drivers"

        cleanup:
        tags?.deleteAll()
        context?.close()
    }

    void "beans injected without a name use the default driver when another driver is configured"() {
        given:
        def context = ApplicationContext.run(['neo4j.uri': EmbeddedNeo4j.boltUri(), 'micronaut.data.neo4j.drivers.other.uri': EmbeddedNeo4j.boltUri()])
        def tags = context.getBean(TagRepository)
        tags.deleteAll()

        expect: "each driver has its own beans, and the unqualified ones are those of the default driver"
        context.getBeansOfType(Neo4jRepositoryOperations).size() == 2
        context.getBean(Neo4jRepositoryOperations).is(context.getBean(Neo4jRepositoryOperations, Qualifiers.byName("default")))
        context.getBean(Neo4jReactorRepositoryOperations).is(context.getBean(Neo4jReactorRepositoryOperations, Qualifiers.byName("default")))
        context.getBean(Neo4jConnectionOperations).is(context.getBean(Neo4jConnectionOperations, Qualifiers.byName("default")))
        context.getBean(Neo4jTransactionOperations).is(context.getBean(Neo4jTransactionOperations, Qualifiers.byName("default")))
        context.getBean(Neo4jReactorTransactionOperations).is(context.getBean(Neo4jReactorTransactionOperations, Qualifiers.byName("default")))

        when: "a plain @Transactional method fails"
        context.getBean(TagService).saveAndFail("rolled back")

        then: "its transaction on the default driver is rolled back"
        thrown(IllegalStateException)
        tags.count() == 0

        cleanup:
        context?.close()
    }

    void "a configured driver needs both a username and a password"() {
        when:
        def context = ApplicationContext.run(['neo4j.uri': EmbeddedNeo4j.boltUri(), 'micronaut.data.neo4j.drivers.other.uri': EmbeddedNeo4j.boltUri(), 'micronaut.data.neo4j.drivers.other.username': 'neo4j'])
        context.getBean(OtherDriverTagRepository).count()

        then:
        def e = thrown(BeanInstantiationException)
        rootCause(e) instanceof ConfigurationException
        rootCause(e).message == 'Neo4j driver [other] needs both micronaut.data.neo4j.drivers.other.username and micronaut.data.neo4j.drivers.other.password, or neither for a server without authentication'

        cleanup:
        context?.close()
    }

    void "a configured driver cannot be named default"() {
        when:
        def context = ApplicationContext.run(['neo4j.uri': EmbeddedNeo4j.boltUri(), 'micronaut.data.neo4j.drivers.default.uri': EmbeddedNeo4j.boltUri()])
        context.getBean(TagRepository).count()

        then:
        def e = thrown(BeanInstantiationException)
        rootCause(e) instanceof ConfigurationException
        rootCause(e).message.startsWith('The Neo4j driver name [default] is reserved for the driver of micronaut-neo4j')

        cleanup:
        context?.close()
    }

    private static Throwable rootCause(Throwable e) {
        while (e.cause != null && e.cause != e) {
            e = e.cause
        }
        return e
    }

    void "sessions of a driver share a bookmark manager unless disabled"() {
        given:
        def enabled = ApplicationContext.run(['neo4j.uri': EmbeddedNeo4j.boltUri()])
        def disabled = ApplicationContext.run(['neo4j.uri': EmbeddedNeo4j.boltUri(), 'micronaut.data.neo4j.bookmarks': false])

        expect:
        enabled.getBean(Neo4jSessionConfigurer, Qualifiers.byName("default")).sessionConfig().bookmarkManager().present
        !disabled.getBean(Neo4jSessionConfigurer, Qualifiers.byName("default")).sessionConfig().bookmarkManager().present

        cleanup:
        enabled?.close()
        disabled?.close()
    }

    void "sessions on other threads and in transactions begin with the bookmarks of the writes before them"() {
        given:
        def context = ApplicationContext.run(['neo4j.uri': EmbeddedNeo4j.boltUri(), 'spec.name': 'Neo4jDriversSpec'])
        def recorder = context.getBean(BookmarkRecorder)
        def tags = context.getBean(TagRepository)
        def service = context.getBean(TagService)
        def executor = Executors.newSingleThreadExecutor()
        def otherThread = executor.submit({ Thread.currentThread().name } as Callable<String>).get()
        tags.deleteAll()

        when: "a node is written on this thread and read on another"
        recorder.reset()
        def saved = tags.save(new Tag("causal"))
        def written = recorder.received.last()
        recorder.reset()
        def found = executor.submit({ tags.findById(saved.id) } as Callable<Optional<Tag>>).get()

        then: "the session of the other thread shares the bookmark manager, which hands its transaction the bookmark of the write"
        !written.isEmpty()
        recorder.sessions*.thread() == [otherThread]
        recorder.sessions[0].bookmarkManager().is(recorder.manager)
        !recorder.handedOut.isEmpty()
        recorder.handedOut.every { it.containsAll(written) }
        found.get().name == "causal"

        when: "a node is written on the other thread and changed in a transaction on this thread"
        recorder.reset()
        def other = executor.submit({ tags.save(new Tag("other")) } as Callable<Tag>).get()
        def writtenElsewhere = recorder.received.last()
        recorder.reset()
        def renamed = service.renameAndFind(other.id, "renamed")
        def committed = recorder.received.last()

        then: "the transaction runs in one session that begins with the bookmark of the other thread's write"
        renamed.get() == "renamed"
        recorder.sessions*.thread() == [Thread.currentThread().name]
        recorder.sessions[0].bookmarkManager().is(recorder.manager)
        !recorder.handedOut.isEmpty()
        recorder.handedOut.every { it.containsAll(writtenElsewhere) }
        recorder.received.size() == 1
        committed != writtenElsewhere

        when: "the other thread reads after the transaction commits"
        recorder.reset()
        def readElsewhere = executor.submit({ tags.findById(other.id) } as Callable<Optional<Tag>>).get()

        then: "its session begins with the bookmark of the transaction"
        recorder.handedOut.every { it.containsAll(committed) }
        !recorder.handedOut.isEmpty()
        readElsewhere.get().name == "renamed"

        cleanup:
        executor?.shutdownNow()
        context?.close()
    }

    void "sessions use the database of the current tenant"() {
        given:
        def context = ApplicationContext.run(['neo4j.uri': EmbeddedNeo4j.boltUri(), 'micronaut.data.neo4j.database': 'neo4j'])
        def tenant = 'neo4j'
        context.registerSingleton(SchemaTenantResolver, { tenant } as SchemaTenantResolver)
        def tags = context.getBean(TagRepository)

        when:
        tags.count()

        then:
        noExceptionThrown()

        when: "the tenant's database does not exist"
        tenant = 'tenant-without-a-database'
        tags.count()

        then:
        def e = thrown(DataAccessException)
        e.message.toLowerCase().contains('tenant-without-a-database')

        cleanup:
        context?.close()
    }
}

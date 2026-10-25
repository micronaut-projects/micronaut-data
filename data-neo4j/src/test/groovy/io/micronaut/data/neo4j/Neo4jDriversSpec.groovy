package io.micronaut.data.neo4j

import io.micronaut.context.ApplicationContext
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.neo4j.features.OtherDriverTagRepository
import io.micronaut.data.neo4j.features.Tag
import io.micronaut.data.neo4j.features.TagRepository
import io.micronaut.data.neo4j.session.Neo4jDriverRegistration
import io.micronaut.data.neo4j.session.Neo4jSessionConfigurer
import io.micronaut.data.runtime.multitenancy.SchemaTenantResolver
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

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

    void "a write is seen by the next session through the bookmarks of the driver"() {
        given:
        def context = ApplicationContext.run(['neo4j.uri': EmbeddedNeo4j.boltUri()])
        def bookmarks = context.getBean(Neo4jDriverRegistration, Qualifiers.byName("default")).driver().executableQueryBookmarkManager()
        def tags = context.getBean(TagRepository)
        tags.deleteAll()

        expect: "the sessions of the repositories carry the bookmark manager of the driver"
        context.getBean(Neo4jSessionConfigurer, Qualifiers.byName("default")).sessionConfig().bookmarkManager().get().is(bookmarks)

        when: "a node is written in one session"
        def before = bookmarks.bookmarks
        def saved = tags.save(new Tag("causal"))

        then: "the session leaves the bookmark of its transaction in the manager when it closes"
        def after = bookmarks.bookmarks
        !after.isEmpty()
        after != before

        and: "the next repository calls open sessions that start after the write and read it"
        tags.findById(saved.id).get().name == "causal"
        tags.findByName("causal").get().id == saved.id
        tags.count() == 1

        when: "the node is deleted in another session"
        tags.deleteById(saved.id)

        then: "the bookmark moves on and the next read no longer sees the node"
        bookmarks.bookmarks != after
        !tags.existsById(saved.id)

        cleanup:
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

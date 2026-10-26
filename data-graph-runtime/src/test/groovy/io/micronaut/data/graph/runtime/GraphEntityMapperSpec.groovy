package io.micronaut.data.graph.runtime

import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.data.event.EntityEventListener
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.graph.runtime.entities.Actor
import io.micronaut.data.graph.runtime.entities.Director
import io.micronaut.data.graph.runtime.entities.Person
import io.micronaut.data.graph.runtime.entities.Star
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.runtime.RuntimePersistentProperty
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.util.function.Function

/**
 * Checks how nodes are mapped to entities, independently of a database.
 */
class GraphEntityMapperSpec extends Specification {

    @Shared
    // The label lookup reads no values, so the mapper needs no serde
    GraphEntityMapper mapper = new GraphEntityMapper(new TestEntityRegistry(), ConversionService.SHARED, Function.identity(), null)

    @Unroll
    void "a node labelled #labels is read as #expected.simpleName"() {
        when:
        def entity = mapper.readNodeAs(node(labels), Object)

        then:
        expected.isInstance(entity)
        entity.name == 'Keanu'

        where:
        labels                       | expected
        ['Person']                   | Person
        ['Actor', 'Person']          | Actor
        ['Person', 'Actor']          | Actor
        ['Person', 'Director']       | Director
        ['Star', 'Actor', 'Person']  | Star
        ['Person', 'Actor', 'Star']  | Star
        ['Person', 'Alien']          | Person
        ['Actor']                    | Actor
        ['Star']                     | Star
    }

    void "a node whose labels match no entity is reported"() {
        when:
        mapper.readNodeAs(node(['Alien']), Person)

        then:
        def e = thrown(DataAccessException)
        e.message.startsWith('Node with labels [Alien] is not a Person')

        when:
        mapper.readNodeAs(node(['Alien']), Actor)

        then:
        e = thrown(DataAccessException)
        e.message.startsWith('Node with labels [Alien] is not a Actor')

        when: 'nothing is requested'
        def properties = mapper.readNodeAs(node(['Alien']), Object)

        then:
        properties == [name: 'Keanu']
    }

    void "the requested entity wins when the node carries its primary label"() {
        expect:
        mapper.readNodeAs(node(['Actor', 'Person']), Person) instanceof Person
        mapper.readNodeAs(node(['Actor', 'Person']), Actor) instanceof Actor
    }

    void "a node of a more specific entity is read as the requested entity only when it is one"() {
        when:
        mapper.readNodeAs(node(['Person']), Actor)

        then:
        def e = thrown(DataAccessException)
        e.message.startsWith('Node with labels [Person] is not a Actor')
    }

    private static GraphNode node(List<String> labels) {
        return new GraphNode('4:abc:1', labels, [name: 'Keanu'])
    }

    private static final class TestEntityRegistry implements RuntimeEntityRegistry {
        private final Map<Class<?>, RuntimePersistentEntity<?>> entities = [:]

        @Override
        EntityEventListener<Object> getEntityEventListener() {
            throw new IllegalStateException()
        }

        @Override
        Object autoPopulateRuntimeProperty(RuntimePersistentProperty<?> persistentProperty, Object previousValue) {
            throw new IllegalStateException()
        }

        @Override
        <T> RuntimePersistentEntity<T> getEntity(Class<T> type) {
            RuntimeEntityRegistry registry = this
            return (RuntimePersistentEntity<T>) entities.computeIfAbsent(type, { Class entityType ->
                new RuntimePersistentEntity<Object>(entityType) {
                    @Override
                    protected RuntimePersistentEntity<Object> getEntity(Class<Object> nestedType) {
                        return registry.getEntity(nestedType)
                    }
                }
            })
        }

        @Override
        <T> RuntimePersistentEntity<T> newEntity(Class<T> type) {
            throw new IllegalStateException()
        }

        @Override
        ApplicationContext getApplicationContext() {
            throw new IllegalStateException()
        }
    }
}

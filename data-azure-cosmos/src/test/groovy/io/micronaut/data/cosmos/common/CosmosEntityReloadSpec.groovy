package io.micronaut.data.cosmos.common

import io.micronaut.context.ApplicationContext
import io.micronaut.context.reload.ClassChange
import io.micronaut.context.reload.ClassChangeEvent
import io.micronaut.context.reload.ReloadStrategy
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.dev.tck.ReloadHarness
import io.micronaut.dev.tck.ReloadTck
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * The Cosmos entities that {@link CosmosEntity} holds in a static map, keyed by persistent entity, follow the
 * application in development mode and do not keep a retired generation reachable. The Cosmos emulator is not needed:
 * the entries are created as the database initializer creates them, for the document class of each generation.
 * The classes of a generation are only reached from Java, in {@link CosmosReloadSupport}.
 */
class CosmosEntityReloadSpec extends Specification {

    private static final String NOTE = 'example.Note'

    private static final String DOCUMENT = '''
        package example;

        import io.micronaut.data.annotation.Id;
        import io.micronaut.data.annotation.MappedEntity;
        import io.micronaut.data.cosmos.annotation.PartitionKey;

        @MappedEntity
        public class Note {
            @Id
            private String id;
            @PartitionKey
            private String %s;

            public String getId() { return id; }
            public void setId(String id) { this.id = id; }
            public String get%s() { return %s; }
            public void set%s(String value) { this.%s = value; }
        }
        '''

    @TempDir
    Path project

    void "a restart gives the next generation Cosmos entities of its own, and the retired generation is collected"() {
        given:
        ReloadHarness harness = ReloadHarness.inDirectory(project)
        harness.source(NOTE, note('topic'))
        harness.start()
        String first = CosmosReloadSupport.initialize(harness.context(), NOTE)

        when: 'the document class changes its partition key, and the application restarts'
        harness.source(NOTE, note('owner'))
        harness.reload()

        then: 'the persistent entities are equal by name: the Cosmos entity of the first generation is not reused'
        first == '/topic'
        harness.generation() == 2
        CosmosReloadSupport.partitionKey(harness.context(), NOTE) == null
        CosmosReloadSupport.initialize(harness.context(), NOTE) == '/owner'

        and: 'the map no longer keeps the first generation reachable'
        ReloadTck.assertRetiredGenerationsCollected(harness)

        cleanup:
        harness.close()
    }

    void "a reload in place that retires a classloader forgets the Cosmos entities of its classes, and keeps the others"() {
        given:
        ReloadHarness harness = ReloadHarness.inDirectory(project)
        harness.source(NOTE, note('topic'))
        harness.start()
        ApplicationContext context = harness.context()
        CosmosReloadSupport.initialize(context, NOTE)
        ClassLoader loader = context.getClassLoader()

        when: 'an unrelated loader is retired'
        context.publishEvent(new ClassChangeEvent(this, [new URLClassLoader(new URL[0])] as Set, loader, [], ReloadStrategy.RELOAD))

        then:
        CosmosReloadSupport.partitionKey(context, NOTE) == '/topic'

        when: 'the loader of the document class is retired'
        context.publishEvent(new ClassChangeEvent(this, [loader] as Set, loader, [new ClassChange(NOTE, ClassChange.Kind.MODIFIED)], ReloadStrategy.RELOAD))

        then:
        CosmosReloadSupport.partitionKey(context, NOTE) == null

        cleanup:
        harness.close()
    }

    void "stopping the context in development mode forgets the Cosmos entities of its classes"() {
        given:
        ReloadHarness harness = ReloadHarness.inDirectory(project)
        harness.source(NOTE, note('topic'))
        harness.start()
        ApplicationContext context = harness.context()
        CosmosReloadSupport.initialize(context, NOTE)
        RuntimePersistentEntity<?> plain = context.getBean(RuntimeEntityRegistry).getEntity(Plain)
        CosmosEntity.create(plain, null)

        when:
        harness.close()
        CosmosEntity.get(plain)

        then: 'a class the context sees, of the parent tier too'
        thrown(NullPointerException)
    }

    void "outside development mode the Cosmos entities are kept"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        RuntimePersistentEntity<?> entity = context.getBean(RuntimeEntityRegistry).getEntity(Plain)
        CosmosEntity.create(entity, null)

        expect:
        !context.containsBean(DevelopmentCosmosReloader)

        when:
        context.publishEvent(new ClassChangeEvent(this, [Plain.classLoader] as Set, Plain.classLoader, [], ReloadStrategy.RELOAD))
        context.close()

        then:
        CosmosEntity.get(entity).containerName == 'plain'
    }

    private static String note(String property) {
        String capitalized = property.capitalize()
        return DOCUMENT.formatted(property, capitalized, property, capitalized, property)
    }
}

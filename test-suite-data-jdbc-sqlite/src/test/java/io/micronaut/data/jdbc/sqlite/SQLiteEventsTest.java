package io.micronaut.data.jdbc.sqlite;

import io.micronaut.data.tck.entities.DomainEvents;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@MicronautTest
@SQLiteDBProperties
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SQLiteEventsTest {

    @Inject
    SQLiteDomainEventsRepository eventsRepository;

    @Inject
    SQLiteDomainEventsReactiveRepository eventsReactiveRepository;

    private DomainEvents entityUnderTest;

    @Test
    @Order(1)
    void testPreAndPostPersistEventTriggered() {
        entityUnderTest = new DomainEvents();
        entityUnderTest.setName("test");
        eventsRepository.save(entityUnderTest);

        assertCounters(entityUnderTest, 1, 1, 0, 0, 0, 0, 0);
    }

    @Test
    @Order(2)
    void testPostLoadEventTriggered() {
        DomainEvents loaded = eventsRepository.findById(entityUnderTest.getUuid()).orElse(null);

        assertNotNull(loaded);
        assertCounters(loaded, 0, 0, 0, 0, 0, 0, 1);
    }

    @Test
    @Order(3)
    void testsPreAndPostUpdateEventsTriggered() {
        entityUnderTest.setName("changed");
        eventsRepository.update(entityUnderTest);

        assertCounters(entityUnderTest, 1, 1, 1, 1, 0, 0, 0);
    }

    @Test
    @Order(4)
    void testsPreAndPostRemoveEventsTriggered() {
        entityUnderTest.setName("changed");
        eventsRepository.delete(entityUnderTest);

        assertCounters(entityUnderTest, 1, 1, 1, 1, 1, 1, 0);
    }

    @Test
    @Order(5)
    void testPreAndPostPersistEventTriggeredReactive() {
        entityUnderTest = new DomainEvents();
        entityUnderTest.setName("test");
        eventsReactiveRepository.save(entityUnderTest).blockingGet();

        assertCounters(entityUnderTest, 1, 1, 0, 0, 0, 0, 0);
    }

    @Test
    @Order(6)
    void testPostLoadEventTriggeredReactive() {
        DomainEvents loaded = eventsReactiveRepository.findById(entityUnderTest.getUuid()).blockingGet();

        assertNotNull(loaded);
        assertCounters(loaded, 0, 0, 0, 0, 0, 0, 1);
    }

    @Test
    @Order(7)
    void testsPreAndPostUpdateEventsTriggeredReactive() {
        entityUnderTest.setName("changed");
        eventsReactiveRepository.update(entityUnderTest).blockingGet();

        assertCounters(entityUnderTest, 1, 1, 1, 1, 0, 0, 0);
    }

    @Test
    @Order(8)
    void testsPreAndPostRemoveEventsTriggeredReactive() {
        entityUnderTest.setName("changed");
        eventsReactiveRepository.delete(entityUnderTest).blockingGet();

        assertCounters(entityUnderTest, 1, 1, 1, 1, 1, 1, 0);
    }

    private void assertCounters(DomainEvents domainEvents,
                                int prePersist,
                                int postPersist,
                                int preUpdate,
                                int postUpdate,
                                int preRemove,
                                int postRemove,
                                int postLoad) {
        assertEquals(prePersist, domainEvents.getPrePersist());
        assertEquals(postPersist, domainEvents.getPostPersist());
        assertEquals(preUpdate, domainEvents.getPreUpdate());
        assertEquals(postUpdate, domainEvents.getPostUpdate());
        assertEquals(preRemove, domainEvents.getPreRemove());
        assertEquals(postRemove, domainEvents.getPostRemove());
        assertEquals(postLoad, domainEvents.getPostLoad());
    }
}

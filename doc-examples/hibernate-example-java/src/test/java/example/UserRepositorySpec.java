package example;

import io.micronaut.transaction.TransactionOperations;
import org.hibernate.Session;
import org.hibernate.query.MutationQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public abstract class UserRepositorySpec {

    @Inject UserRepository userRepository;
    @Inject protected TransactionOperations<Session> transactionOperations;

    @BeforeEach
    void setup() {
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    void testSoftDelete() {
        final User joe = new User();
        joe.setName("Joe");
        final User fred = new User();
        fred.setName("Fred");
        final User bob = new User();
        bob.setName("Bob");
        userRepository.saveAll(Arrays.asList(
                fred,
                bob,
                joe
        ));

        userRepository.deleteById(joe.getId());

        assertEquals(2, userRepository.count());
        assertTrue(userRepository.existsById(fred.getId()));
        assertFalse(userRepository.existsById(joe.getId()));

        final List<User> disabled = userRepository.findDisabled();
        assertEquals(1, disabled.size());
        assertEquals("Joe", disabled.iterator().next().getName());
    }

    @Test
    void testStreaming() {
        long total = 500_000L;
        seedUsers(total);

        // tag::stream_and_count[]
        long entityCount = transactionOperations.executeRead( status -> {
            Session session = status.getConnection();
            long count;
            // Execute query with annotated FetchSize
            try (Stream<User> s = userRepository.listAll()) {
                AtomicLong n = new AtomicLong();
                count = s
                    .peek(p -> {
                        session.detach(p);
                        if ((n.incrementAndGet() % 50_000) == 0) {
                            session.clear();
                        }
                    })
                    .map(p -> 1L)
                    .reduce(0L, Long::sum);
            }
            return count;
        });
        // end::stream_and_count[]
        Assertions.assertEquals(total, entityCount);

        entityCount = transactionOperations.executeRead( status -> {
            Session session = status.getConnection();
            // Execute query with default fetch size
            long count;
            try (Stream<User> s = userRepository.queryAll()) {
                count = s
                    .peek(p -> {
                        session.detach(p);
                    })
                    .map(p -> 1L)
                    .reduce(0L, Long::sum);
            }
            return count;
        });
        Assertions.assertEquals(total, entityCount);
    }

    protected void seedUsers(long count) {
        String sql = """
            INSERT INTO user(id, name, enabled)
                   SELECT x, 'Name ' || x, true
                   FROM SYSTEM_RANGE(0, ?);
        """.stripIndent();
        // Execute via Hibernate Session to avoid requiring @Connectable on DataSource
        transactionOperations.executeWrite(status -> {
            Session session = status.getConnection();
            MutationQuery query = session.createNativeMutationQuery(sql);
            query.setParameter(1, count - 1);
            query.executeUpdate();
            return null;
        });
    }
}

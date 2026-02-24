package example

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.TransactionOperations
import jakarta.inject.Inject
import org.hibernate.Session
import org.hibernate.query.MutationQuery
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicLong
import java.util.stream.Stream

@MicronautTest(transactional = false, rollback = false)
@Property(name = "jpa.default.properties.hibernate.hbm2ddl.auto", value = "create-drop")
class UserRepositorySpec extends Specification {

    @Inject UserRepository userRepository
    @Inject TransactionOperations<Session> transactionOperations

    void setup() {
        userRepository.deleteAll()
    }

    void cleanup() {
        userRepository.deleteAll()
    }

    void testSoftDelete() {
        when:
        def joe = userRepository.save(new User(name: "Joe"))
        def fred = userRepository.save(new User(name: "Fred"))
        def bob = userRepository.save(new User(name: "Bob"))
        userRepository.deleteById(joe.id)

        then:
        userRepository.count() == 2
        userRepository.existsById(fred.id)
        !userRepository.existsById(joe.id)

        when:
        def disabled = userRepository.findDisabled()

        then:
        disabled.size() == 1
        disabled.iterator().next().name == "Joe"
    }

    void testStreaming() {
        given:
        long total = 500_000L
        seedUsers(total)

        when:
        // tag::stream_and_count[]
        long entityCount = transactionOperations.executeRead( status -> {
            Session session = status.getConnection()
            long count
            // Execute query with annotated FetchSize
            try (Stream<User> s = userRepository.listAll()) {
                AtomicLong n = new AtomicLong()
                count = s
                        .peek(p -> {
                            session.detach(p)
                            if ((n.incrementAndGet() % 50_000) == 0) {
                                session.clear()
                            }
                        })
                        .map(p -> 1L)
                        .reduce(0L, Long::sum)
            }
            return count
        })
        // end::stream_and_count[]
        then:
        entityCount == total

        when:
        entityCount = transactionOperations.executeRead( status -> {
            Session session = status.getConnection()
            // Execute query with default fetch size
            long count
            try (Stream<User> s = userRepository.queryAll()) {
                count = s
                        .peek(p -> {
                            session.detach(p)
                        })
                        .map(p -> 1L)
                        .reduce(0L, Long::sum)
            }
            return count
        })
        then:
        entityCount == total
    }

    void seedUsers(long count) {
        String sql = """
            INSERT INTO user(id, name, enabled)
                   SELECT x, 'Name ' || x, true
                   FROM SYSTEM_RANGE(10, ?)
        """.stripIndent()
        // Execute via Hibernate Session to avoid requiring @Connectable on DataSource
        transactionOperations.executeWrite(status -> {
            Session session = status.getConnection()
            MutationQuery query = session.createNativeMutationQuery(sql)
            query.setParameter(1, count + 10 - 1)
            query.executeUpdate()
            return null
        })
    }
}

package io.micronaut.data.jdbc.oraclexe.batch

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.oraclexe.OracleTestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.util.concurrent.CopyOnWriteArrayList

class OracleSequenceBatchInsertSpec extends Specification implements OracleTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties + ['spec.name': 'OracleSequenceBatchInsertSpec'])

    @Shared
    SequenceBookRepository repository = context.getBean(SequenceBookRepository)

    @Shared
    StatementRecorder recorder = context.getBean(StatementRecorder)

    @Override
    List<String> packages() {
        return [getClass().package.name]
    }

    void setup() {
        repository.deleteAll()
        recorder.calls.clear()
    }

    void "saveAll inserts entities with sequence ids in one batch and assigns each its id"() {
        when:
        List<SequenceBook> saved = repository.saveAll((1..5).collect { new SequenceBook(title: "Book $it") })

        then: "one batch, not one insert per entity"
        recorder.calls.count('executeBatch') == 1
        recorder.calls.count('executeUpdate') == 0

        and: "each entity gets the id of its own row"
        saved*.id.every { it != null }
        saved*.id.toUnique().size() == 5
        saved.every { book -> repository.findById(book.id).get().title == book.title }
    }

    void "saveAll of entities that are not returned still inserts them in one batch"() {
        when:
        repository.persistAll((1..3).collect { new SequenceBook(title: "Forgotten $it") })

        then:
        recorder.calls.count('executeBatch') == 1
        recorder.calls.count('executeUpdate') == 0
        repository.count() == 3
    }

    /**
     * Records the execute calls of the prepared statements of the default data source.
     */
    @Singleton
    @Requires(property = 'spec.name', value = 'OracleSequenceBatchInsertSpec')
    static class StatementRecorder implements BeanCreatedEventListener<DataSource> {

        final List<String> calls = new CopyOnWriteArrayList<>()

        @Override
        DataSource onCreated(BeanCreatedEvent<DataSource> event) {
            DataSource dataSource = event.bean
            return proxy(DataSource, dataSource) { Method method, Object result ->
                method.name == 'getConnection' ? recordingConnection((Connection) result) : result
            }
        }

        private Connection recordingConnection(Connection connection) {
            return proxy(Connection, connection) { Method method, Object result ->
                result instanceof PreparedStatement ? recordingStatement((PreparedStatement) result) : result
            }
        }

        private PreparedStatement recordingStatement(PreparedStatement statement) {
            return proxy(PreparedStatement, statement) { Method method, Object result ->
                if (method.name in ['executeBatch', 'executeUpdate']) {
                    calls << method.name
                }
                result
            }
        }

        private static <T> T proxy(Class<T> type, T target, Closure<Object> afterInvoke) {
            return (T) Proxy.newProxyInstance(type.classLoader, [type] as Class[], { proxy, Method method, Object[] args ->
                Object result
                try {
                    result = method.invoke(target, args)
                } catch (InvocationTargetException e) {
                    throw e.cause
                }
                return afterInvoke.call(method, result)
            } as InvocationHandler)
        }
    }
}

@MappedEntity("sequence_book")
class SequenceBook {
    @Id
    @GeneratedValue
    Long id
    String title
}

@Requires(property = 'spec.name', value = 'OracleSequenceBatchInsertSpec')
@JdbcRepository(dialect = Dialect.ORACLE)
interface SequenceBookRepository extends CrudRepository<SequenceBook, Long> {

    void persistAll(Iterable<SequenceBook> books)
}

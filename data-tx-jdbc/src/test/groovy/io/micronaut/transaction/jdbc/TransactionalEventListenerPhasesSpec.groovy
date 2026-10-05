package io.micronaut.transaction.jdbc

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventPublisher
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.micronaut.transaction.annotation.TransactionalEventListener
import jakarta.inject.Inject
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import spock.lang.Specification

import java.sql.Connection

/**
 * Verifies the behaviour documented for {@link TransactionalEventListener}.
 */
@MicronautTest(transactional = false)
@Property(name = "datasources.default.name", value = "phasesdb")
@Property(name = "spec.name", value = "TransactionalEventListenerPhasesSpec")
class TransactionalEventListenerPhasesSpec extends Specification {

    @Inject PhasesService service
    @Inject PhasesListener listener
    @Inject RowWriter writer

    void setup() {
        writer.init()
        listener.calls.clear()
        listener.failBeforeCommit = false
        listener.failAfterCommit = false
        listener.writeAfterCommit = null
    }

    void "listeners run on the publishing thread in their transaction phase, not when the event is published"() {
        when:
            service.publishInTransaction(false)

        then:
            service.callsWhenPublishReturned == []
            listener.calls*.phase.first() == "BEFORE_COMMIT"
            listener.calls*.phase as Set == ["BEFORE_COMMIT", "AFTER_COMMIT", "AFTER_COMPLETION"] as Set
            listener.calls.size() == 3
            listener.calls*.thread.unique() == [service.publishingThread]
            listener.calls.every { !it.inPublishCall }
            writer.count() == 1
    }

    void "after rollback only the AFTER_ROLLBACK and AFTER_COMPLETION listeners run"() {
        when:
            service.publishInTransaction(true)

        then:
            thrown(IllegalStateException)
            listener.calls*.phase as Set == ["AFTER_ROLLBACK", "AFTER_COMPLETION"] as Set
            listener.calls.size() == 2
            writer.count() == 0
    }

    void "the event is dropped when no transaction is active"() {
        when:
            service.publishWithoutTransaction()

        then:
            listener.calls.isEmpty()
    }

    void "an exception from a BEFORE_COMMIT listener rolls the transaction back"() {
        given:
            listener.failBeforeCommit = true

        when:
            service.publishInTransaction(false)

        then:
            def e = thrown(RuntimeException)
            e.message == "before commit failure"
            listener.calls*.phase.first() == "BEFORE_COMMIT"
            listener.calls*.phase as Set == ["BEFORE_COMMIT", "AFTER_ROLLBACK", "AFTER_COMPLETION"] as Set
            listener.calls.size() == 3
            writer.count() == 0
    }

    void "an exception from an AFTER_COMMIT listener reaches the caller but the transaction stays committed"() {
        given:
            listener.failAfterCommit = true

        when:
            service.publishInTransaction(false)

        then:
            def e = thrown(RuntimeException)
            e.message == "after commit failure"
            writer.count() == 1
    }

    void "an AFTER_COMMIT listener can write in a new transaction"() {
        given:
            listener.writeAfterCommit = "requiresNew"

        when:
            service.publishInTransaction(false)

        then:
            writer.count() == 2
    }

    @Requires(property = "spec.name", value = "TransactionalEventListenerPhasesSpec")
    @Singleton
    static class RowWriter {
        @Inject Connection connection

        @Transactional
        void init() {
            connection.prepareStatement("drop table phase_row if exists").execute()
            connection.prepareStatement("create table phase_row (id bigint not null auto_increment, primary key (id))").execute()
        }

        @Transactional
        void insert() {
            connection.prepareStatement("insert into phase_row default values").execute()
        }

        @Transactional(Transactional.TxType.REQUIRES_NEW)
        void insertInNewTransaction() {
            connection.prepareStatement("insert into phase_row default values").execute()
        }

        @Transactional
        int count() {
            def rs = connection.prepareStatement("select count(*) from phase_row").executeQuery()
            rs.next()
            return rs.getInt(1)
        }
    }

    @Requires(property = "spec.name", value = "TransactionalEventListenerPhasesSpec")
    @Singleton
    static class PhasesService {
        @Inject ApplicationEventPublisher<PhaseEvent> publisher
        @Inject PhasesListener listener
        @Inject RowWriter writer
        Thread publishingThread
        List<Call> callsWhenPublishReturned

        @Transactional
        void publishInTransaction(boolean fail) {
            writer.insert()
            publishingThread = Thread.currentThread()
            listener.publishing = true
            try {
                publisher.publishEvent(new PhaseEvent())
            } finally {
                listener.publishing = false
            }
            callsWhenPublishReturned = new ArrayList<>(listener.calls)
            if (fail) {
                throw new IllegalStateException("rollback")
            }
        }

        void publishWithoutTransaction() {
            publisher.publishEvent(new PhaseEvent())
        }
    }

    @Requires(property = "spec.name", value = "TransactionalEventListenerPhasesSpec")
    @Singleton
    static class PhasesListener {
        @Inject RowWriter writer
        final List<Call> calls = Collections.synchronizedList([])
        volatile boolean publishing
        volatile boolean failBeforeCommit
        volatile boolean failAfterCommit
        volatile String writeAfterCommit

        @TransactionalEventListener(TransactionalEventListener.TransactionPhase.BEFORE_COMMIT)
        void beforeCommit(PhaseEvent event) {
            record("BEFORE_COMMIT")
            if (failBeforeCommit) {
                throw new RuntimeException("before commit failure")
            }
        }

        @TransactionalEventListener(TransactionalEventListener.TransactionPhase.AFTER_COMMIT)
        void afterCommit(PhaseEvent event) {
            record("AFTER_COMMIT")
            if (failAfterCommit) {
                throw new RuntimeException("after commit failure")
            }
            if (writeAfterCommit == "requiresNew") {
                writer.insertInNewTransaction()
            }
        }

        @TransactionalEventListener(TransactionalEventListener.TransactionPhase.AFTER_ROLLBACK)
        void afterRollback(PhaseEvent event) {
            record("AFTER_ROLLBACK")
        }

        @TransactionalEventListener(TransactionalEventListener.TransactionPhase.AFTER_COMPLETION)
        void afterCompletion(PhaseEvent event) {
            record("AFTER_COMPLETION")
        }

        private void record(String phase) {
            calls.add(new Call(phase, Thread.currentThread(), publishing))
        }
    }

    static class PhaseEvent {
    }

    static class Call {
        final String phase
        final Thread thread
        final boolean inPublishCall

        Call(String phase, Thread thread, boolean inPublishCall) {
            this.phase = phase
            this.thread = thread
            this.inPublishCall = inPublishCall
        }
    }
}

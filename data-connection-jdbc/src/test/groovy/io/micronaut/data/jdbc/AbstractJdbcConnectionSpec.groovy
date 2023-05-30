package io.micronaut.data.jdbc


import io.micronaut.data.connection.exceptions.NoConnectionException
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.connection.jdbc.operations.DataSourceConnectionOperationsImpl
import io.micronaut.data.connection.manager.synchronous.ConnectionOperations
import io.micronaut.data.connection.manager.synchronous.SynchronousConnectionManager
import io.micronaut.data.tck.repositories.SimpleBookRepository
import io.micronaut.data.tck.tests.AbstractConnectionSpec
import io.micronaut.inject.qualifiers.Qualifiers
import org.slf4j.LoggerFactory

import javax.sql.DataSource

abstract class AbstractJdbcConnectionSpec extends AbstractConnectionSpec {

    def LOG = LoggerFactory.getLogger(this.class)

    DataSource dataSource = DelegatingDataSource.unwrapDataSource(context.getBean(DataSource.class, Qualifiers.byName("default")))

    @Override
    Class<? extends SimpleBookRepository> getBookRepositoryClass() {
        throw new IllegalStateException()
    }

    @Override
    protected ConnectionOperations<?> getConnectionOperations() {
        return context.getBean(DataSourceConnectionOperationsImpl.class)
    }

    @Override
    protected SynchronousConnectionManager<?> getSynchronousConnectionManager() {
        return context.getBean(DataSourceConnectionOperationsImpl.class)
    }

    List<String> createStatements() {
        // We want id on the second column to test scenario getting auto generated id not on the first position
        return Arrays.asList("CREATE TABLE patient(name VARCHAR(255), id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, history VARCHAR(1000), doctor_notes VARCHAR(255), appointments JSON)")
    }

    List<String> dropStatements() {
        return Arrays.asList("DROP TABLE patient")
    }

    String insertStatement() {
        return "INSERT INTO patient (name, history, doctor_notes) VALUES (?, ?, ?)"
    }

    def setupSpec() {
        Thread.sleep(2000)
    }

    void createSchema() {
        try (def conn = dataSource.getConnection()) {
            createStatements().forEach(query -> {
                println query
                try (def statement = conn.prepareStatement(query)) {
                    statement.executeUpdate()
                }
            })
        } catch (Exception e) {
            LOG.warn("Error creating schema manually: " + e.getMessage(), e)
        }
    }

    void dropSchema() {
        try (def conn = dataSource.getConnection()) {
            dropStatements().forEach(st -> {
                try (def statement = conn.prepareStatement(st)) {
                    statement.executeUpdate()
                }
            })
        } catch (Exception e) {
            LOG.warn("Error dropping schema manually: " + e.getMessage(), e)
        }
    }

    private void createData() {
        def name = "pt1"
        def history = "flu"
        def doctorNotes = "mild"
        def appointments = List.of("Dr1 April 2022", "Dr2 June 2022")
        assert context.getBean(JdbcConnectionService)
                .insertRecord(insertStatement(), name, history, doctorNotes) == 1
    }

    def setup() {
        dropSchema()
        createSchema()
        createData()
    }

    void "test get a connection using execute"() {
        expect:
            getConnectionService().findIdByPatientName("pt1") == 1
    }

    void "test get a connection using advise"() {
        expect:
            getConnectionService().findIdByPatientNameAdvised("pt1") == 1
    }

    void "test get a requires new connection using advise"() {
        expect:
            getConnectionService().findIdByPatientNameAdvisedRequiresNew("pt1") == 1
    }

    void "test get a requires new connection mandatory"() {
        when:
            getConnectionService().findIdByPatientNameAdvisedRequiresMandatory("pt1")
        then:
            def e = thrown(NoConnectionException)
            e.message == "No existing connection found for connection marked with propagation 'mandatory'"

        when:
            long result = getConnectionService().findIdByPatientNameAdvisedRequiresMandatory2("pt1")
        then:
            result == 1

    }

    private JdbcConnectionService getConnectionService() {
        return context.getBean(JdbcConnectionService)
    }
}

package io.micronaut.data.jdbc;

import io.micronaut.data.connection.manager.ConnectionDefinition;
import io.micronaut.data.connection.manager.synchronous.ConnectionOperations;
import io.micronaut.data.connection.manager.synchronous.ConnectionStatus;
import jakarta.inject.Singleton;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Singleton
public class JdbcConnectionService {

    private final ConnectionOperations<Connection> connectionOperations;

    public JdbcConnectionService(ConnectionOperations<Connection> connectionOperations) {
        this.connectionOperations = connectionOperations;
    }

    public Integer insertRecord(String sql, String name, String history, String doctorNotes) {
        return connectionOperations.executeWrite(status -> {
            try {
                return insertRecord(status.getConnection(), sql, name, history, doctorNotes);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private Integer insertRecord(Connection connection, String sql, String name, String history, String doctorNotes) throws SQLException {
        try (PreparedStatement insertStmt = connection.prepareStatement(sql)) {
            insertStmt.setString(1, name);
            insertStmt.setString(2, history);
            insertStmt.setString(3, doctorNotes);
            return insertStmt.executeUpdate();
        }
    }

    public Long findIdByPatientName(String name) {
        return connectionOperations.executeRead(status -> findIdByPatientName(status.getConnection(), name));
    }

    @io.micronaut.data.connection.annotation.Connection
    public Long findIdByPatientNameAdvised(String name) {
        return findIdByPatientName(
            getRequiredConnection(),
            name
        );
    }

    @io.micronaut.data.connection.annotation.Connection
    public Long findIdByPatientNameAdvisedRequiresNew(String name) {
        return findIdByPatientNameAdvisedRequiresNewWrapped(
            getRequiredConnection(),
            name
        );
    }

    @io.micronaut.data.connection.annotation.Connection(propagation = ConnectionDefinition.Propagation.MANDATORY)
    public Long findIdByPatientNameAdvisedRequiresMandatory(String name) {
        return findIdByPatientName(getRequiredConnection(), name);
    }

    @io.micronaut.data.connection.annotation.Connection
    public Long findIdByPatientNameAdvisedRequiresMandatory2(String name) {
        return findIdByPatientNameAdvisedRequiresMandatory(name);
    }

    @io.micronaut.data.connection.annotation.Connection(propagation = ConnectionDefinition.Propagation.REQUIRES_NEW)
    protected Long findIdByPatientNameAdvisedRequiresNewWrapped(Connection existingConnection, String name) {
        Connection newConnection = getRequiredConnection();
        if (newConnection == existingConnection || newConnection.equals(existingConnection)) {
            throw new IllegalStateException("New connection is required!");
        }
        return findIdByPatientName(
            newConnection,
            name
        );
    }

    private Connection getRequiredConnection() {
        return connectionOperations.findConnectionStatus().map(ConnectionStatus::getConnection).orElseThrow();
    }

    private long findIdByPatientName(Connection connection, String name) {
        try (PreparedStatement statement = connection.prepareStatement("select id from patient where name = ?")) {
            statement.setString(1, name);
            statement.execute();
            ResultSet resultSet = statement.getResultSet();
            resultSet.next();
            return resultSet.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

}

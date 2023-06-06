package io.micronaut.data.jdbc.h2;

import io.micronaut.data.connection.jdbc.operations.DefaultDataSourceConnectionOperations;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.repositories.SimpleBookRepository;
import jakarta.inject.Singleton;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Singleton
public class H2BookRepository implements SimpleBookRepository {

    private final DefaultDataSourceConnectionOperations dataSourceConnectionOperations;

    public H2BookRepository(DefaultDataSourceConnectionOperations dataSourceConnectionOperations) {
        this.dataSourceConnectionOperations = dataSourceConnectionOperations;
    }

    private Connection getConnection() {
        return dataSourceConnectionOperations.getConnectionStatus().getConnection();
    }

    @Override
    public Book save(Book book) {
        try (PreparedStatement statement = getConnection().prepareStatement("insert into book(title, total_pages) values (?, ?);")) {
            statement.setString(1, book.getTitle());
            statement.setInt(2, book.getTotalPages());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return book;
    }

    @Override
    public void deleteAll() {
        try (Statement statement = getConnection().createStatement()) {
            statement.execute("delete from book;");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public long count() {
        return dataSourceConnectionOperations.executeRead(status -> {
            try (Statement statement = status.getConnection().createStatement()) {
                try (ResultSet resultSet = statement.executeQuery("select count(*) from book;")) {
                    resultSet.next();
                    return resultSet.getLong(1);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
}

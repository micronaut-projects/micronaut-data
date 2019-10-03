package io.micronaut.transaction.jdbc;

import io.micronaut.transaction.SynchronousTransactionManager;
import io.micronaut.transaction.TransactionDefinition;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@Singleton
public class TestService {
    @Inject
    Connection connection;
    @Inject
    SynchronousTransactionManager<Connection> transactionManager;

    void init() {
        transactionManager.executeWrite(status -> {
            connection.prepareStatement("drop table book if exists").execute();
            connection.prepareStatement("create table book (id bigint not null auto_increment, pages integer not null, title varchar(255), primary key (id))").execute();
            return null;
        });
    }

    void insertWithTransaction() {
        transactionManager.executeWrite(status -> {
            try (PreparedStatement ps = connection
                    .prepareStatement("insert into book (pages, title) values(100, 'The Stand')")) {
                ps.execute();
            }
            return null;
        });
    }

    void insertAndRollback() {
        transactionManager.execute(TransactionDefinition.DEFAULT, status -> {
            try(PreparedStatement ps1 = connection
                    .prepareStatement("insert into book (pages, title) values(200, 'The Shining')")) {

                ps1.execute();
                throw new RuntimeException("Bad things happened");
            }
        });
    }


    void insertAndRollbackChecked() {
        transactionManager.execute(TransactionDefinition.DEFAULT, status -> {
            try(PreparedStatement ps1 = connection
                    .prepareStatement("insert into book (pages, title) values(200, 'The Shining')")) {

                ps1.execute();
                throw new Exception("Bad things happened");
            }
        });
    }


    int readTransactionally() {
        return transactionManager.executeRead(status -> {
            try (PreparedStatement ps = connection.prepareStatement("select count(*) as count from book")) {
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getInt("count");
                }
            }
        });
    }
}

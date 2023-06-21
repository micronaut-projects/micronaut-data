package io.micronaut.data.hibernate.connection;

import io.micronaut.data.connection.ConnectionOperations;
import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.repositories.SimpleBookRepository;
import jakarta.inject.Singleton;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.MutationQuery;
import org.hibernate.query.Query;

@Singleton
public class HibernateBookRepositoryImpl implements SimpleBookRepository {

    private final ConnectionOperations<Session> connectionOperations;

    public HibernateBookRepositoryImpl(ConnectionOperations<Session> connectionOperations) {
        this.connectionOperations = connectionOperations;
    }

    @Override
    public Book save(Book book) {
        return connectionOperations.executeWrite(status -> {
            Session session = status.getConnection();
            Transaction transaction = session.beginTransaction();
            session.persist(book);
            transaction.commit();
            return book;
        });
    }

    @Override
    public void deleteAll() {
        connectionOperations.executeWrite(status -> {
            Session session = status.getConnection();
            Transaction transaction = session.beginTransaction();
            MutationQuery mutationQuery = session.createMutationQuery(session.getCriteriaBuilder().createCriteriaDelete(Book.class));
            mutationQuery.executeUpdate();
            transaction.commit();
            return null;
        });
    }

    @Override
    public long count() {
        return connectionOperations.executeRead(status -> {
            Query<Long> query = status.getConnection().createQuery("SELECT COUNT(*) FROM io.micronaut.data.tck.entities.Book", Long.class);
            return query.getSingleResult();
        });
    }

}

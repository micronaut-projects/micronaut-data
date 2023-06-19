package io.micronaut.data.hibernate.connection;

import io.micronaut.data.tck.entities.Book;
import io.micronaut.data.tck.repositories.SimpleBookRepository;
import jakarta.inject.Singleton;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.hibernate.Session;
import org.hibernate.Transaction;

@Singleton
public class SimpleBookRepositoryImpl implements SimpleBookRepository {

    private final HibernateConnectionOperations connectionOperations;

    public SimpleBookRepositoryImpl(HibernateConnectionOperations connectionOperations) {
        this.connectionOperations = connectionOperations;
    }

    @Override
    public Book save(Book book) {
        Session session = getSession();
        Transaction transaction = session.beginTransaction();
        session.persist(book);
        transaction.commit();
        return book;
    }

    @Override
    public void deleteAll() {
        Session session = getSession();
        Transaction transaction = session.beginTransaction();
        CriteriaBuilder builder = session.getCriteriaBuilder();
        CriteriaDelete<Book> query = builder.createCriteriaDelete(Book.class);
        session.createMutationQuery(query).executeUpdate();
        transaction.commit();
    }

    @Override
    public long count() {
        return connectionOperations.executeRead(status -> {
            Session session = status.getConnection();
            CriteriaBuilder builder = session.getCriteriaBuilder();
            CriteriaQuery<Long> query = builder.createQuery(Long.class);
            Root<Book> root = query.from(Book.class);
            query.select(builder.count(root.get("id")));
            return session.createQuery(query).getSingleResult();
        });
    }

    private Session getSession() {
        return connectionOperations.getConnectionStatus().getConnection();
    }

}

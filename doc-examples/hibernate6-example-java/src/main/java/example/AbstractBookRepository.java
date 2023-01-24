
package example;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.repository.CrudRepository;

import jakarta.persistence.EntityManager;
import java.util.List;

@Repository
public abstract class AbstractBookRepository implements CrudRepository<Book, Long> {

    private final EntityManager entityManager;

    public AbstractBookRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<Book> findByTitle(String title) {
        return entityManager.createQuery("FROM Book AS book WHERE book.title = :title", Book.class)
                    .setParameter("title", title)
                    .getResultList();
    }
}

package example

import io.micronaut.configuration.hibernate.jpa.scope.CurrentSession
import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository

import javax.persistence.EntityManager

@Repository
abstract class AbstractBookRepository implements CrudRepository<Book, Long> {

    private final EntityManager entityManager

    AbstractBookRepository(@CurrentSession EntityManager entityManager) {
        this.entityManager = entityManager
    }

    List<Book> findByTitle(String title) {
        return entityManager.createQuery("FROM Book AS book WHERE book.title = :title", Book)
                .setParameter("title", title)
                .getResultList()
    }
}

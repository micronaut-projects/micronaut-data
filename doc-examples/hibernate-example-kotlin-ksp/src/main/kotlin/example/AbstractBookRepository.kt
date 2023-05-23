
package example

import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository

import jakarta.persistence.EntityManager

@Repository
abstract class AbstractBookRepository(private val entityManager: EntityManager) : CrudRepository<Book, Long> {

    fun findByTitle(title: String): List<Book> {
        return entityManager.createQuery("FROM Book AS book WHERE book.title = :title", Book::class.java)
                .setParameter("title", title)
                .resultList
    }
}

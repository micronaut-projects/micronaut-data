
package example

import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.kotlin.KotlinCrudRepository

import javax.persistence.EntityManager

@Repository
abstract class AbstractBookRepository(private val entityManager: EntityManager) : KotlinCrudRepository<Book, Long> {

    fun findByTitle(title: String): List<Book> {
        return entityManager.createQuery("FROM Book AS book WHERE book.title = :title", Book::class.java)
                .setParameter("title", title)
                .resultList
    }
}

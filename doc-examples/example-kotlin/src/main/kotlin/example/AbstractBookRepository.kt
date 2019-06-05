package example

import io.micronaut.configuration.hibernate.jpa.scope.CurrentSession
import io.micronaut.data.annotation.Repository
import io.micronaut.data.repository.CrudRepository

import javax.persistence.EntityManager

@Repository
abstract class AbstractBookRepository(@param:CurrentSession private val entityManager: EntityManager) : CrudRepository<Book, Long> {

    fun findByTitle(title: String): List<Book> {
        return entityManager.createQuery("FROM Book AS book WHERE book.title = :title", Book::class.java)
                .setParameter("title", title)
                .resultList
    }
}


package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Slice
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import java.util.function.Consumer
import javax.transaction.Transactional

// tag::repository[]
@Repository // <1>
interface BookRepository : CoroutineCrudRepository<Book, Long> { // <2>

    // tag::read[]
    suspend fun find(title: String): Book

    suspend fun findOne(title: String): BookDTO

    suspend fun findByPagesGreaterThan(pageCount: Int, pageable: Pageable): List<Book>

    suspend fun findByTitleLike(title: String, pageable: Pageable): Page<Book>

    suspend fun list(pageable: Pageable): Slice<Book>
    // end::read[]

    // tag::save[]
    suspend fun save(entity: Book): Book
    // end::save[]

    // tag::inserts[]
    @Query("INSERT INTO Book(title, pages) VALUES (:title, :pages)")
    suspend fun insert(title: String, pages: Int)

    // tag::update[]
    @Transactional
    suspend fun findByIdAndUpdate(id: Long, bookConsumer: Consumer<Book?>) {
        bookConsumer.accept(findById(id))
    }

    suspend fun update(newBook: Book): Book

    suspend fun update(@Id id: Long?, pages: Int)
    // end::update[]

    // tag::delete[]
    suspend fun delete(title: String)
    // end::delete[]
}
// end::repository[]

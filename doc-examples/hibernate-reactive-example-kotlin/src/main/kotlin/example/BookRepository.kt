
// tag::repository[]
package example

import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Repository
import io.micronaut.data.model.Page
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Slice
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import reactor.core.publisher.Mono
import java.util.function.Consumer
import java.util.function.Function
import javax.transaction.Transactional

@Repository // <1>
interface BookRepository : CoroutineCrudRepository<Book, Long> { // <2>
// end::repository[]

    // tag::simple-alt[]
    // tag::repository[]
    suspend fun find(title: String): Book
    // end::simple-alt[]
    // end::repository[]

    // tag::pageable[]
    suspend fun findByPagesGreaterThan(pageCount: Int, pageable: Pageable): List<Book>

    suspend fun findByTitleLike(title: String, pageable: Pageable): Page<Book>

    suspend fun list(pageable: Pageable): Slice<Book>
    // end::pageable[]

    // tag::save[]
    suspend fun save(entity: Book): Book
    // end::save[]

    // tag::inserts[]
    @Query("INSERT INTO Book(title, pages) VALUES (:title, :pages)")
    suspend fun insert(title: String, pages: Int)

    // tag::update[]
    suspend fun update(newBook: Book): Book
    // end::update[]

    // tag::update1[]
    suspend fun update(@Id id: Long?, pages: Int)
    // end::update1[]

    // tag::deleteone[]
    suspend fun delete(title: String)
    // end::deleteone[]

    // tag::dto[]
    suspend fun findOne(title: String): BookDTO
    // end::dto[]

    @Transactional
    suspend fun findByIdAndUpdate(id: Long, bookConsumer: Consumer<Book?>) {
        bookConsumer.accept(findById(id))
    }

// tag::repository[]
}
// end::repository[]

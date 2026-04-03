package example

import io.micronaut.core.annotation.NonNull
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.sql.Procedure
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.r2dbc.annotation.R2dbcRepository
import io.micronaut.data.repository.jpa.criteria.CriteriaQueryBuilder
import io.micronaut.data.repository.kotlin.CoroutineCrudRepository
import jakarta.transaction.Transactional
import kotlinx.coroutines.flow.Flow
import reactor.core.publisher.Mono

@R2dbcRepository(dialect = Dialect.POSTGRES) // <1>
interface BookRepository : CoroutineCrudRepository<Book, Long> {
    @Join("author")
    override suspend fun findById(id: Long): Book? // <2>

    @Join("author")
    override fun findAll(): Flow<Book>

    suspend fun findTitleById(id: Long): String?

    // tag::mandatory[]
    @Transactional(Transactional.TxType.MANDATORY)
    override suspend fun <S : Book> save(entity: S): S

    @Transactional(Transactional.TxType.MANDATORY)
    override fun <S : Book> saveAll(entities: Iterable<S>): Flow<S>
    // end::mandatory[]

    @Query("SELECT * FROM book WHERE title = :title")
    suspend fun customFindOne(title: String): BookDTO?

    suspend fun findOne(title: String): BookDTO?

    fun findAll(specification: CriteriaQueryBuilder<BookDTO>): Flow<BookDTO>

    // tag::procedure[]
    @Procedure
    suspend fun calculateSum(bookId: @NonNull Long): Long
    // end::procedure[]

    suspend fun saveReturning(book: Book): Book

    fun saveReturningMany(books: Iterable<Book>): Flow<Book>

    suspend fun saveReturningManyAsList(books: Iterable<Book>): List<Book>

    @Query("UPDATE book SET title = :title, pages = :pages WHERE id = :id RETURNING *")
    suspend fun updateReturning(id: Long, title: String, pages: Int): Book

    @Query("UPDATE book SET pages = :pages WHERE id IN (:ids) RETURNING *")
    fun updateReturningMany(ids: Iterable<Long>, pages: Int): Flow<Book>

    @Query("UPDATE book SET pages = :pages WHERE id IN (:ids) RETURNING *")
    suspend fun updateReturningManyAsList(ids: Iterable<Long>, pages: Int): List<Book>

    @Query("DELETE FROM book WHERE id = :id RETURNING *")
    suspend fun deleteReturning(id: Long): Book

    @Query("DELETE FROM book WHERE id IN (:ids) RETURNING *")
    fun deleteReturningMany(ids: Iterable<Long>): Flow<Book>

    @Query("DELETE FROM book WHERE id IN (:ids) RETURNING *")
    suspend fun deleteReturningManyAsList(ids: Iterable<Long>): List<Book>

}

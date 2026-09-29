package example

import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import java.util.Optional

@Client("/books")
interface BookClient {

    @Post
    fun save(title: String, pages: Int): BookDto

    @Get("/{id}")
    fun findOne(id: String): Optional<BookDto>

    @Get
    fun findAll(): List<BookDto>

    @Delete
    fun deleteAll()
}

package example;

import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.client.annotation.Client;

import java.util.List;
import java.util.Optional;

@Client("/books")
public interface BookClient {

    @Post
    BookDto save(String title, int pages);

    @Get("/{id}")
    Optional<BookDto> findOne(String id);

    @Get
    List<BookDto> findAll();

    @Delete
    void deleteAll();
}

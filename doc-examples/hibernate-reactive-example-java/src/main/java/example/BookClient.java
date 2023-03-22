
package example;

import io.micronaut.data.model.Page;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;

@Client("/books")
public interface BookClient {
    @Get("/{title}")
    Mono<Page<Book>> findByTitleLike(
            String title,
            @QueryValue int page,
            @QueryValue int size,
            @QueryValue(defaultValue = "title, desc") String sort);

    @Post("/")
    Mono<Book> save(@Valid Book book);
}


package example;

import io.micronaut.data.model.Page;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;

import jakarta.validation.Valid;

@Client("/init")
public interface InitClient {

    @Post("/")
    void init();
}

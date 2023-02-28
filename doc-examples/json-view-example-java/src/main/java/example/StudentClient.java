
package example;

import example.domain.view.StudentView;
import io.micronaut.data.model.Page;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;

import javax.validation.Valid;
import java.util.List;
import java.util.Optional;

@Client("/students")
public interface StudentClient {
    @Get("/view")
    List<StudentView> view();

    @Get("/{id}")
    Optional<StudentView> show(Long id);
}

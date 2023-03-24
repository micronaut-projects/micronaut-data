
package example;

import example.domain.UsrDto;
import example.domain.view.StudentView;
import example.domain.view.UsrView;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;

import java.util.List;
import java.util.Optional;

@Client("/students")
public interface StudentClient {
    @Get("/view")
    List<StudentView> view();

    @Get("/{id}")
    Optional<StudentView> show(Long id);

    @Get("/user/{id}")
    Optional<UsrDto> user(Long id);

    @Get("/user_view/{id}")
    Optional<UsrView> userView(Long id);
}

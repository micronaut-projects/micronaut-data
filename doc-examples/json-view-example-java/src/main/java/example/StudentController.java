
package example;

import example.domain.view.StudentView;
import example.repository.StudentViewRepository;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import javax.validation.Valid;
import java.util.List;
import java.util.Optional;

@ExecuteOn(TaskExecutors.IO)
@Controller("/students")
public class StudentController {

    protected final StudentViewRepository studentViewRepository;

    public StudentController(StudentViewRepository studentViewRepository) {
        this.studentViewRepository = studentViewRepository;
    }

    @Get("/{id}")
    public Optional<StudentView> show(Long id) {
        return studentViewRepository.findByStudentId(id);
    }

    @Get("/view")
    public List<StudentView> view() {
        return studentViewRepository.findAll();
    }

}

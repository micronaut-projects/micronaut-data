package example;

import example.domain.view.StudentView;
import io.micronaut.data.model.Page;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

@MicronautTest
public class StudentClientSpec {

    @Inject InitClient initClient;
    @Inject StudentClient studentClient;

    @Test
    void testStudentClient() {
        // TODO: Didn't work
        // initClient.init();
        List<StudentView> students = studentClient.view();

        Assertions.assertEquals(
                3,
            students.size()
        );

        Optional<StudentView> optStudentView = studentClient.show(students.get(0).studentId().longValue());

        Assertions.assertTrue(optStudentView.isPresent());
        StudentView studentView = optStudentView.get();
        Assertions.assertNotNull(studentView);
    }
}

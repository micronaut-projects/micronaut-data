package example;

import example.domain.UsrDto;
import example.domain.view.StudentView;
import example.domain.view.UsrView;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

@MicronautTest
class StudentClientSpec {

    @Inject StudentClient studentClient;

    @Test
    void testStudentClient() {

        Optional<UsrDto> optUsr = studentClient.user(1L);
        Assertions.assertTrue(optUsr.isPresent());
        UsrDto usr = optUsr.get();
        Assertions.assertEquals(1L, usr.getId());

        Optional<UsrView> optUsrView = studentClient.userView(1L);
        Assertions.assertTrue(optUsrView.isPresent());
        UsrView usrView = optUsrView.get();
        Assertions.assertEquals(1L, usrView.getUsrId());

/*        List<StudentView> students = studentClient.view();

        Assertions.assertEquals(
                3,
            students.size()
        );

        Optional<StudentView> optStudentView = studentClient.show(students.get(0).studentId());

        Assertions.assertTrue(optStudentView.isPresent());
        StudentView studentView = optStudentView.get();
        Assertions.assertNotNull(studentView); */
    }
}

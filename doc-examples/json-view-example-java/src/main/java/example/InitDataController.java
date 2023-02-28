

package example;

import example.domain.Class;
import example.domain.Student;
import example.domain.StudentClass;
import example.domain.Teacher;
import example.repository.ClassRepository;
import example.repository.StudentClassRepository;
import example.repository.StudentRepository;
import example.repository.TeacherRepository;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;

import java.time.LocalTime;

@ExecuteOn(TaskExecutors.IO)
@Controller("/init")
public class InitDataController {

    private final StudentRepository studentRepository;
    private final ClassRepository classRepository;
    private final StudentClassRepository studentClassRepository;
    private final TeacherRepository teacherRepository;

    public InitDataController(StudentRepository studentRepository,
                              ClassRepository classRepository,
                              StudentClassRepository studentClassRepository,
                              TeacherRepository teacherRepository) {
        this.studentRepository = studentRepository;
        this.classRepository = classRepository;
        this.studentClassRepository = studentClassRepository;
        this.teacherRepository = teacherRepository;
    }

    @Post
    public void init() {
        studentClassRepository.deleteAll();
        classRepository.deleteAll();
        teacherRepository.deleteAll();
        studentRepository.deleteAll();

        Teacher teacherAnna = teacherRepository.save(new Teacher("Mrs. Anna"));
        Teacher teacherJeff = teacherRepository.save(new Teacher("Mr. Jeff"));

        Student denis = studentRepository.save(new Student("Denis"));
        Student josh = studentRepository.save(new Student("Josh"));
        Student fred = studentRepository.save(new Student("Fred"));

        Class math = classRepository.save(new Class("Math", "A101", LocalTime.of(10, 00), teacherAnna));
        Class english = classRepository.save(new Class("English", "A102", LocalTime.of(11, 00), teacherJeff));
        Class german = classRepository.save(new Class("German", "A103", LocalTime.of(12, 00), teacherAnna));

        studentClassRepository.save(new StudentClass(denis, math));
        studentClassRepository.save(new StudentClass(josh, math));
        studentClassRepository.save(new StudentClass(fred, math));

        studentClassRepository.save(new StudentClass(denis, german));
        studentClassRepository.save(new StudentClass(josh, english));
        studentClassRepository.save(new StudentClass(fred, german));
    }

}

package io.micronaut.data.jdbc.sqlite.assignedid;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AssignedUuidManyToManyPersistTest {

    @Disabled("Cascade update does not remove existing link records, issue https://github.com/micronaut-projects/micronaut-data/issues/3722")
    @Test
    void shouldPersistJoinRowsWithAssignedUuidsViaCascadePersistAndSupportUpdate() {
        try (ApplicationContext ctx = ApplicationContext.run(createProperties())) {
            JdbcStudentRepository studentRepository = ctx.getBean(JdbcStudentRepository.class);
            JdbcCourseRepository courseRepository = ctx.getBean(JdbcCourseRepository.class);

            Student student = new Student();
            student.setId(UUID.randomUUID());
            student.setName("Denis");
            Course course1 = new Course();
            course1.setId(UUID.randomUUID());
            course1.setName("Math");
            Course course2 = new Course();
            course2.setId(UUID.randomUUID());
            course2.setName("Physics");

            courseRepository.save(course1);
            courseRepository.save(course2);
            student.addCourse(course1);
            student.addCourse(course2);

            studentRepository.save(student);
            Student saved = studentRepository.findById(student.getId()).orElse(null);

            assertNotNull(saved);
            assertEquals(Set.of(course1.getId(), course2.getId()), saved.getCourses().stream().map(Course::getId).collect(Collectors.toSet()));

            Student student2 = new Student();
            student2.setId(UUID.randomUUID());
            student2.setName("John");
            student2.addCourse(course1);
            studentRepository.save(student2);
            Student found = studentRepository.findById(student2.getId()).orElse(null);

            assertNotNull(found);
            assertEquals(Set.of(course1.getId()), found.getCourses().stream().map(Course::getId).collect(Collectors.toSet()));

            course1.setName("Mathematics");
            student2.setCourses(List.of(course1));
            studentRepository.update(student2);
            Student found2 = studentRepository.findById(student2.getId()).orElse(null);

            assertNotNull(found2);
            assertEquals(Set.of("Mathematics"), found2.getCourses().stream().map(Course::getName).collect(Collectors.toSet()));
        }
    }

    private static Map<String, Object> createProperties() {
        try {
            var databaseFile = Files.createTempFile("assigneduuidmanytomanypersist", ".sqlite").toFile();
            databaseFile.deleteOnExit();
            Map<String, Object> properties = new HashMap<>();
            properties.put("datasources.default.url", "jdbc:sqlite:" + databaseFile.getAbsolutePath());
            properties.put("datasources.default.schema-generate", "CREATE");
            properties.put("datasources.default.dialect", "ANSI");
            properties.put("datasources.default.db-type", "sqlite");
            properties.put("datasources.default.username", "");
            properties.put("datasources.default.password", "");
            properties.put("datasources.default.packages", "io.micronaut.data.jdbc.sqlite.assignedid");
            properties.put("datasources.default.driverClassName", "org.sqlite.JDBC");
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create SQLite test database", e);
        }
    }
}

@MappedEntity("student_assigned")
class Student {

    @Id
    private UUID id;

    @NotBlank
    private String name;

    @Relation(value = Relation.Kind.MANY_TO_MANY, cascade = {Relation.Cascade.PERSIST, Relation.Cascade.UPDATE})
    private List<Course> courses = new ArrayList<>();

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    List<Course> getCourses() {
        return courses;
    }

    void setCourses(List<Course> courses) {
        this.courses = courses;
    }

    void addCourse(Course course) {
        if (course != null) {
            courses.add(course);
        }
    }
}

@MappedEntity("course_assigned")
class Course {

    @Id
    private UUID id;

    @NotBlank
    private String name;

    @Relation(value = Relation.Kind.MANY_TO_MANY, mappedBy = "courses")
    private List<Student> students = new ArrayList<>();

    UUID getId() {
        return id;
    }

    void setId(UUID id) {
        this.id = id;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    List<Student> getStudents() {
        return students;
    }

    void setStudents(List<Student> students) {
        this.students = students;
    }
}

@JdbcRepository(dialect = Dialect.ANSI)
interface JdbcStudentRepository extends CrudRepository<Student, UUID> {

    @Join("courses")
    Optional<Student> findById(UUID id);
}

@JdbcRepository(dialect = Dialect.ANSI)
interface JdbcCourseRepository extends CrudRepository<Course, UUID> {
}

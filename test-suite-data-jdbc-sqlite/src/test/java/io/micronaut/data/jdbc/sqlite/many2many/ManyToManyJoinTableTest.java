package io.micronaut.data.jdbc.sqlite.many2many;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.EmbeddedId;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.sql.JoinColumn;
import io.micronaut.data.annotation.sql.JoinTable;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.sqlite.SQLiteDBProperties;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
@SQLiteDBProperties(packages = "io.micronaut.data.jdbc.sqlite.many2many")
class ManyToManyJoinTableTest {

    @Inject
    StudentRepository studentRepository;

    @Inject
    CourseRepository courseRepository;

    @Inject
    CourseRatingRepository courseRatingRepository;

    @Inject
    CourseRatingCompositeKeyRepository courseRatingCompositeKeyRepository;

    private final Map<Class<?>, RuntimePersistentEntity> entities = new HashMap<>();

    @Test
    void testManyToManyHierarchy() {
        Student student = new Student();
        student.setName("Denis");
        Course math = new Course();
        math.setName("Math");
        Course physics = new Course();
        physics.setName("Physics");
        student.setCourses(List.of(math, physics));

        student = studentRepository.save(student);
        student = studentRepository.findById(student.getId()).orElseThrow();

        assertNotNull(student.getId());
        assertEquals("Denis", student.getName());
        assertEquals(2, student.getCourses().size());
        assertNotNull(student.getCourses().get(0).getId());
        assertEquals("Math", student.getCourses().get(0).getName());
        assertNotNull(student.getCourses().get(1).getId());
        assertEquals("Physics", student.getCourses().get(1).getName());

        List<Course> courses = courseRepository.findAll();
        assertEquals(2, courses.size());
        assertEquals("Math", courses.get(0).getName());
        assertEquals(1, courses.get(0).getStudents().size());
        assertEquals(student.getId(), courses.get(0).getStudents().get(0).getId());
        assertEquals("Denis", courses.get(0).getStudents().get(0).getName());
        assertEquals("Physics", courses.get(1).getName());
        assertEquals(1, courses.get(1).getStudents().size());
        assertEquals(student.getId(), courses.get(1).getStudents().get(0).getId());
        assertEquals("Denis", courses.get(1).getStudents().get(0).getName());

        CourseRating rating = new CourseRating();
        rating.setStudent(student);
        rating.setCourse(student.getCourses().get(1));
        rating.setRating(5);
        courseRatingRepository.save(rating);
        student = studentRepository.queryById(student.getId()).orElseThrow();

        assertEquals("Denis", student.getName());
        assertEquals(2, student.getCourses().size());
        assertEquals(1, student.getRatings().size());
        CourseRating loadedRating = student.getRatings().iterator().next();
        assertNotNull(loadedRating.getId());
        assertEquals(student, loadedRating.getStudent());
        assertEquals("Physics", loadedRating.getCourse().getName());
        assertEquals(5, loadedRating.getRating());

        CourseRatingKey key = new CourseRatingKey();
        key.setStudent(student);
        key.setCourse(student.getCourses().get(1));
        CourseRatingCompositeKey compositeRating = new CourseRatingCompositeKey();
        compositeRating.setId(key);
        compositeRating.setRating(5);
        courseRatingCompositeKeyRepository.save(compositeRating);
        student = studentRepository.findByIdEquals(student.getId()).orElseThrow();

        assertEquals("Denis", student.getName());
        assertEquals(2, student.getCourses().size());
        assertEquals(1, student.getRatingsCK().size());
        CourseRatingCompositeKey loadedComposite = student.getRatingsCK().iterator().next();
        assertEquals(student, loadedComposite.getId().getStudent());
        assertEquals("Physics", loadedComposite.getId().getCourse().getName());
        assertEquals(5, loadedComposite.getRating());
    }

    @Test
    void testBuildCreateStudentTables() {
        SqlQueryBuilder encoder = new SqlQueryBuilder();
        String[] statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(Student.class));

        assertEquals(2, statements.length);
        assertEquals("CREATE TABLE \"m2m_student_course_association\" (\"st_id\" INTEGER NOT NULL,\"cs_id\" INTEGER NOT NULL, PRIMARY KEY(\"st_id\",\"cs_id\"));", statements[0]);
        assertEquals("CREATE TABLE \"m2m_student\" (\"id\" INTEGER PRIMARY KEY,\"name\" VARCHAR(255) NOT NULL);", statements[1]);
    }

    @Test
    void testBuildCreateCourseRatingTables() {
        SqlQueryBuilder encoder = new SqlQueryBuilder();
        String[] statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(CourseRating.class));

        assertEquals(1, statements.length);
        assertEquals("CREATE TABLE \"m2m_course_rating\" (\"id\" INTEGER PRIMARY KEY,\"student_id\" INTEGER NOT NULL,\"course_id\" INTEGER NOT NULL,\"rating\" INTEGER NOT NULL);", statements[0]);
    }

    @Test
    void testBuildCreateCourseTables() {
        SqlQueryBuilder encoder = new SqlQueryBuilder();
        String[] statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(Course.class));

        assertEquals(1, statements.length);
        assertEquals("CREATE TABLE \"m2m_course\" (\"id\" INTEGER PRIMARY KEY,\"name\" VARCHAR(255) NOT NULL);", statements[0]);
    }

    @Test
    void testBuildStudentSelectWithCourses() {
        RuntimeCriteriaBuilder builder = new RuntimeCriteriaBuilder();
        var query = builder.createQuery(Student.class);
        var root = query.from(Student.class);
        root.join("courses", Join.Type.FETCH);
        var builtQuery = query.where(builder.equal(root.id(), builder.parameter(Object.class))).build(new SqlQueryBuilder());

        assertEquals("SELECT student_.\"id\",student_.\"name\",student_courses_.\"id\" AS courses_id,student_courses_.\"name\" AS courses_name FROM \"m2m_student\" student_ INNER JOIN \"m2m_student_course_association\" student_courses_m2m_student_course_association_ ON student_.\"id\"=student_courses_m2m_student_course_association_.\"st_id\"  INNER JOIN \"m2m_course\" student_courses_ ON student_courses_m2m_student_course_association_.\"cs_id\"=student_courses_.\"id\" WHERE (student_.\"id\" = ?)", builtQuery.getQuery());
        assertEquals(Map.of("1", "id"), builtQuery.getParameters());
    }

    @Test
    void testBuildStudentSelectWithRatings() {
        RuntimeCriteriaBuilder builder = new RuntimeCriteriaBuilder();
        var query = builder.createQuery(Student.class);
        var root = query.from(Student.class);
        root.join("ratings", Join.Type.FETCH);
        var builtQuery = query.where(builder.equal(root.id(), builder.parameter(Object.class))).build(new SqlQueryBuilder());

        assertEquals("SELECT student_.\"id\",student_.\"name\",student_ratings_.\"id\" AS ratings_id,student_ratings_.\"student_id\" AS ratings_student_id,student_ratings_.\"course_id\" AS ratings_course_id,student_ratings_.\"rating\" AS ratings_rating FROM \"m2m_student\" student_ INNER JOIN \"m2m_course_rating\" student_ratings_ ON student_.\"id\"=student_ratings_.\"student_id\" WHERE (student_.\"id\" = ?)", builtQuery.getQuery());
        assertEquals(Map.of("1", "id"), builtQuery.getParameters());
    }

    @Test
    void testBuildInsert() {
        SqlQueryBuilder encoder = new SqlQueryBuilder();
        RuntimePersistentEntity<?> entity = getRuntimePersistentEntity(Student.class);
        String query = encoder.buildJoinTableInsert(entity, (Association) entity.getPropertyByName("courses"));

        assertEquals("INSERT INTO \"m2m_student_course_association\" (\"st_id\",\"cs_id\") VALUES (?,?)", query);
    }

    @Test
    void testBuildCourseRatingCompositeKeyInsert() {
        RuntimeCriteriaBuilder builder = new RuntimeCriteriaBuilder();
        var insert = builder.createCriteriaInsert(CourseRatingCompositeKey.class).build(new SqlQueryBuilder());

        assertEquals("INSERT INTO \"m2m_course_rating_ck\" (\"rating\",\"xyz_student_id\",\"abc_course_id\") VALUES (?,?,?)", insert.getQuery());
    }

    private RuntimePersistentEntity getRuntimePersistentEntity(Class type) {
        RuntimePersistentEntity entity = entities.get(type);
        if (entity == null) {
            entity = new RuntimePersistentEntity(type) {
                @Override
                protected RuntimePersistentEntity getEntity(Class t) {
                    return getRuntimePersistentEntity(t);
                }
            };
            entities.put(type, entity);
        }
        return entity;
    }
}

@JdbcRepository(dialect = Dialect.ANSI)
interface StudentRepository extends CrudRepository<Student, Long> {

    @Join(value = "courses", type = Join.Type.LEFT_FETCH)
    @Override
    Optional<Student> findById(Long id);

    @Join(value = "courses", type = Join.Type.LEFT_FETCH)
    @Join(value = "ratings", type = Join.Type.LEFT_FETCH)
    @Join(value = "ratings.course", type = Join.Type.LEFT_FETCH)
    Optional<Student> queryById(Long id);

    @Join(value = "courses", type = Join.Type.LEFT_FETCH)
    @Join(value = "ratingsCK", type = Join.Type.LEFT_FETCH)
    @Join(value = "ratingsCK.id.course", type = Join.Type.LEFT_FETCH)
    Optional<Student> findByIdEquals(Long id);

    int countDistinctByCoursesRatingsRatingInList(List<Integer> ratings);
}

@JdbcRepository(dialect = Dialect.ANSI)
interface CourseRepository extends CrudRepository<Course, Long> {

    @Join(value = "students", type = Join.Type.LEFT_FETCH)
    @Override
    List<Course> findAll();
}

@JdbcRepository(dialect = Dialect.ANSI)
interface CourseRatingRepository extends CrudRepository<CourseRating, Long> {

    @Join(value = "student", type = Join.Type.LEFT_FETCH)
    @Join(value = "course", type = Join.Type.LEFT_FETCH)
    @Override
    List<CourseRating> findAll();

    @Join(value = "student", type = Join.Type.LEFT_FETCH)
    @Join(value = "course", type = Join.Type.LEFT_FETCH)
    @Override
    Optional<CourseRating> findById(Long id);
}

@JdbcRepository(dialect = Dialect.ANSI)
interface CourseRatingCompositeKeyRepository extends CrudRepository<CourseRatingCompositeKey, CourseRatingKey> {

    @Join(value = "student", type = Join.Type.LEFT_FETCH)
    @Join(value = "course", type = Join.Type.LEFT_FETCH)
    @Override
    List<CourseRatingCompositeKey> findAll();

    @Join(value = "student", type = Join.Type.LEFT_FETCH)
    @Join(value = "course", type = Join.Type.LEFT_FETCH)
    @Override
    Optional<CourseRatingCompositeKey> findById(CourseRatingKey id);
}

@MappedEntity(value = "m2m_student")
class Student {

    @Id
    @GeneratedValue
    private Long id;
    private String name;

    @JoinTable(
        name = "m2m_student_course_association",
        joinColumns = @JoinColumn(name = "st_id"),
        inverseJoinColumns = @JoinColumn(name = "cs_id")
    )
    @Relation(value = Relation.Kind.MANY_TO_MANY, cascade = Relation.Cascade.PERSIST)
    private List<Course> courses;

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "student")
    private Set<CourseRating> ratings;

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "student")
    private Set<CourseRatingCompositeKey> ratingsCK;

    Long getId() {
        return id;
    }

    void setId(Long id) {
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

    Set<CourseRating> getRatings() {
        return ratings;
    }

    void setRatings(Set<CourseRating> ratings) {
        this.ratings = ratings;
    }

    Set<CourseRatingCompositeKey> getRatingsCK() {
        return ratingsCK;
    }

    void setRatingsCK(Set<CourseRatingCompositeKey> ratingsCK) {
        this.ratingsCK = ratingsCK;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Student student)) {
            return false;
        }
        return Objects.equals(id, student.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

@MappedEntity(value = "m2m_course")
class Course {

    @Id
    @GeneratedValue
    private Long id;
    private String name;

    @Relation(value = Relation.Kind.MANY_TO_MANY, mappedBy = "courses")
    private List<Student> students;

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "course")
    private Set<CourseRating> ratings;

    Long getId() {
        return id;
    }

    void setId(Long id) {
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

    Set<CourseRating> getRatings() {
        return ratings;
    }

    void setRatings(Set<CourseRating> ratings) {
        this.ratings = ratings;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Course course)) {
            return false;
        }
        return Objects.equals(id, course.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

@MappedEntity(value = "m2m_course_rating")
class CourseRating {

    @Id
    @GeneratedValue
    private Long id;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private Student student;

    @Relation(Relation.Kind.MANY_TO_ONE)
    private Course course;

    private int rating;

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    Student getStudent() {
        return student;
    }

    void setStudent(Student student) {
        this.student = student;
    }

    Course getCourse() {
        return course;
    }

    void setCourse(Course course) {
        this.course = course;
    }

    int getRating() {
        return rating;
    }

    void setRating(int rating) {
        this.rating = rating;
    }
}

@MappedEntity(value = "m2m_course_rating_ck")
class CourseRatingCompositeKey {

    @EmbeddedId
    private CourseRatingKey id;

    private int rating;

    CourseRatingKey getId() {
        return id;
    }

    void setId(CourseRatingKey id) {
        this.id = id;
    }

    int getRating() {
        return rating;
    }

    void setRating(int rating) {
        this.rating = rating;
    }
}

@Embeddable
class CourseRatingKey {

    @MappedProperty("xyz_student_id")
    @Relation(Relation.Kind.MANY_TO_ONE)
    private Student student;

    @MappedProperty("abc_course_id")
    @Relation(Relation.Kind.MANY_TO_ONE)
    private Course course;

    Student getStudent() {
        return student;
    }

    void setStudent(Student student) {
        this.student = student;
    }

    Course getCourse() {
        return course;
    }

    void setCourse(Course course) {
        this.course = course;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof CourseRatingKey key)) {
            return false;
        }
        return Objects.equals(student, key.student) && Objects.equals(course, key.course);
    }

    @Override
    public int hashCode() {
        return Objects.hash(student, course);
    }
}

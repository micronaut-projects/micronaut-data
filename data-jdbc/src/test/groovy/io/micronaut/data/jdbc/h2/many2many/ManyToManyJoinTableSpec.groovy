package io.micronaut.data.jdbc.h2.many2many

import groovy.transform.EqualsAndHashCode
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.data.annotation.*
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.annotation.JoinColumn
import io.micronaut.data.jdbc.annotation.JoinTable
import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.Association
import io.micronaut.data.model.query.QueryModel
import io.micronaut.data.model.query.QueryParameter
import io.micronaut.data.model.query.builder.QueryBuilder
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import jakarta.inject.Inject

@MicronautTest
@H2DBProperties
class ManyToManyJoinTableSpec extends Specification implements H2TestPropertyProvider {
    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    StudentRepository studentRepository = applicationContext.getBean(StudentRepository)
    @Shared
    @Inject
    CourseRepository courseRepository = applicationContext.getBean(CourseRepository)
    @Shared
    @Inject
    CourseRatingRepository courseRatingRepository = applicationContext.getBean(CourseRatingRepository)
    @Shared
    @Inject
    CourseRatingCompositeKeyRepository courseRatingCompositeKeyRepository = applicationContext.getBean(CourseRatingCompositeKeyRepository)

    void 'test many-to-many hierarchy'() {
        given:
            Student student = new Student(name: "Denis", courses: [new Course(name: "Math"), new Course(name: "Physics")])
        when:
            student = studentRepository.save(student)
            student = studentRepository.findById(student.id).get()
        then:
            student.id
            student.name == "Denis"
            student.courses.size() == 2
            student.courses.get(0).id
            student.courses.get(0).name == "Math"
            student.courses.get(1).id
            student.courses.get(1).name == "Physics"
        when:
            def courses = courseRepository.findAll().toList()
        then:
            courses.size() == 2
            courses[0].name == "Math"
            courses[0].students.size() == 1
            courses[0].students[0].id == student.id
            courses[0].students[0].name == "Denis"
            courses[1].name == "Physics"
            courses[1].students.size() == 1
            courses[1].students[0].id == student.id
            courses[1].students[0].name == "Denis"
        when:
            def rating = new CourseRating(student: student, course: student.courses.get(1), rating: 5)
            courseRatingRepository.save(rating)
            student = studentRepository.queryById(student.id).get()
        then:
            student.name == "Denis"
            student.courses.size() == 2
            student.ratings.size() == 1
            student.ratings[0].id
            student.ratings[0].student == student
            student.ratings[0].course.name == "Physics"
            student.ratings[0].rating == 5
        when:
            rating = new CourseRatingCompositeKey(id: new CourseRatingKey(student: student, course: student.courses.get(1)), rating: 5)
            courseRatingCompositeKeyRepository.save(rating)
            student = studentRepository.findByIdEquals(student.id).get()
        then:
            student.name == "Denis"
            student.courses.size() == 2
            student.ratingsCK.size() == 1
            student.ratingsCK[0].id.student == student
            student.ratingsCK[0].id.course.name == "Physics"
            student.ratingsCK[0].rating == 5
    }

    void "test build create Student tables"() {
        when:
            QueryBuilder encoder = new SqlQueryBuilder()
            def statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(Student))

        then:
            statements.length == 2
            statements[0] == 'CREATE TABLE "m2m_student_course_association" ("st_id" BIGINT NOT NULL,"cs_id" BIGINT NOT NULL);'
            statements[1] == 'CREATE TABLE "m2m_student" ("id" BIGINT PRIMARY KEY AUTO_INCREMENT,"name" VARCHAR(255) NOT NULL);'
    }

    void "test build create CourseRating tables"() {
        when:
            QueryBuilder encoder = new SqlQueryBuilder()
            def statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(CourseRating))

        then:
            statements.length == 1
            statements[0] == 'CREATE TABLE "m2m_course_rating" ("id" BIGINT PRIMARY KEY AUTO_INCREMENT,"student_id" BIGINT NOT NULL,"course_id" BIGINT NOT NULL,"rating" INT NOT NULL);'
    }

    void "test build create Course tables"() {
        when:
            QueryBuilder encoder = new SqlQueryBuilder()
            def statements = encoder.buildCreateTableStatements(getRuntimePersistentEntity(Course))

        then:
            statements.length == 1
            statements[0] == 'CREATE TABLE "m2m_course" ("id" BIGINT PRIMARY KEY AUTO_INCREMENT,"name" VARCHAR(255) NOT NULL);'
    }

    void "test build Student select with courses"() {
        when:
            QueryBuilder encoder = new SqlQueryBuilder()
            def queryModel = QueryModel.from(getRuntimePersistentEntity(Student))
            queryModel.join("courses", Join.Type.FETCH, null)
            def q = encoder.buildQuery(queryModel.idEq(new QueryParameter("id")))
        then:
            q.query == 'SELECT student_."id",student_."name",student_courses_."id" AS courses_id,student_courses_."name" AS courses_name FROM "m2m_student" student_ INNER JOIN "m2m_student_course_association" student_courses_m2m_student_course_association_ ON student_."id"=student_courses_m2m_student_course_association_."st_id"  INNER JOIN "m2m_course" student_courses_ ON student_courses_m2m_student_course_association_."cs_id"=student_courses_."id" WHERE (student_."id" = ?)'
            q.parameters == ['1': 'id']
    }

    void "test build Student select with ratings"() {
        when:
            QueryBuilder encoder = new SqlQueryBuilder()
            def queryModel = QueryModel.from(getRuntimePersistentEntity(Student))
            queryModel.join("ratings", Join.Type.FETCH, null)
            def q = encoder.buildQuery(queryModel.idEq(new QueryParameter("id")))
        then:
            q.query == 'SELECT student_."id",student_."name",student_ratings_."id" AS ratings_id,student_ratings_."student_id" AS ratings_student_id,student_ratings_."course_id" AS ratings_course_id,student_ratings_."rating" AS ratings_rating FROM "m2m_student" student_ INNER JOIN "m2m_course_rating" student_ratings_ ON student_."id"=student_ratings_."student_id" WHERE (student_."id" = ?)'
            q.parameters == ['1': 'id']
    }

    void "test build insert"() {
        when:
            QueryBuilder encoder = new SqlQueryBuilder()
            def e = getRuntimePersistentEntity(Student)
            def query = encoder.buildJoinTableInsert(e, e.getPropertyByName("courses") as Association)

        then:
            query == 'INSERT INTO "m2m_student_course_association" ("st_id","cs_id") VALUES (?,?)'
    }

    void "test build CourseRatingCompositeKey insert"() {
        when:
            QueryBuilder encoder = new SqlQueryBuilder()
            def e = getRuntimePersistentEntity(CourseRatingCompositeKey)
            def insert = encoder.buildInsert(AnnotationMetadata.EMPTY_METADATA, e)

        then:
            insert.query == 'INSERT INTO "m2m_course_rating_ck" ("rating","xyz_student_id","abc_course_id") VALUES (?,?,?)'
    }

    @Shared
    Map<Class, RuntimePersistentEntity> entities = [:]

    // entities have instance compare in some cases
    private RuntimePersistentEntity getRuntimePersistentEntity(Class type) {
        RuntimePersistentEntity entity = entities.get(type)
        if (entity == null) {
            entity = new RuntimePersistentEntity(type) {
                @Override
                protected RuntimePersistentEntity getEntity(Class t) {
                    return getRuntimePersistentEntity(t)
                }
            }
            entities.put(type, entity)
        }
        return entity
    }
}

@JdbcRepository(dialect = Dialect.H2)
interface StudentRepository extends CrudRepository<Student, Long> {

    @Join(value = "courses", type = Join.Type.LEFT_FETCH)
    @Override
    Optional<Student> findById(Long aLong)

    @Join(value = "courses", type = Join.Type.LEFT_FETCH)
    @Join(value = "ratings", type = Join.Type.LEFT_FETCH)
    @Join(value = "ratings.course", type = Join.Type.LEFT_FETCH)
    Optional<Student> queryById(Long aLong)

    @Join(value = "courses", type = Join.Type.LEFT_FETCH)
    @Join(value = "ratingsCK", type = Join.Type.LEFT_FETCH)
    @Join(value = "ratingsCK.id.course", type = Join.Type.LEFT_FETCH)
    Optional<Student> findByIdEquals(Long id)

    int countDistinctByCoursesRatingsRatingInList(List<Integer> ratings);

}

@JdbcRepository(dialect = Dialect.H2)
interface CourseRepository extends CrudRepository<Course, Long> {

    @Join(value = "students", type = Join.Type.LEFT_FETCH)
    @Override
    List<Course> findAll()
}

@JdbcRepository(dialect = Dialect.H2)
interface CourseRatingRepository extends CrudRepository<CourseRating, Long> {

    @Join(value = "student", type = Join.Type.LEFT_FETCH)
    @Join(value = "course", type = Join.Type.LEFT_FETCH)
    @Override
    List<CourseRating> findAll()

    @Join(value = "student", type = Join.Type.LEFT_FETCH)
    @Join(value = "course", type = Join.Type.LEFT_FETCH)
    @Override
    Optional<CourseRating> findById(Long id)
}

@JdbcRepository(dialect = Dialect.H2)
interface CourseRatingCompositeKeyRepository extends CrudRepository<CourseRatingCompositeKey, CourseRatingKey> {

    @Join(value = "student", type = Join.Type.LEFT_FETCH)
    @Join(value = "course", type = Join.Type.LEFT_FETCH)
    @Override
    List<CourseRatingCompositeKey> findAll()

    @Join(value = "student", type = Join.Type.LEFT_FETCH)
    @Join(value = "course", type = Join.Type.LEFT_FETCH)
    @Override
    Optional<CourseRatingCompositeKey> findById(CourseRatingKey id)
}

@EqualsAndHashCode(includes = "id")
@MappedEntity("m2m_student")
class Student {
    @Id
    @GeneratedValue
    Long id
    String name
    @JoinTable(
            name = "m2m_student_course_association",
            joinColumns = @JoinColumn(name = "st_id"),
            inverseJoinColumns = @JoinColumn(name = "cs_id"))
    @Relation(value = Relation.Kind.MANY_TO_MANY, cascade = Relation.Cascade.PERSIST)
    List<Course> courses
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "student")
    Set<CourseRating> ratings
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "student")
    Set<CourseRatingCompositeKey> ratingsCK
}

@EqualsAndHashCode(includes = "id")
@MappedEntity("m2m_course")
class Course {
    @Id
    @GeneratedValue
    Long id
    String name
    @Relation(value = Relation.Kind.MANY_TO_MANY, mappedBy = "courses")
    List<Student> students
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "course")
    Set<CourseRating> ratings
}

@MappedEntity("m2m_course_rating")
class CourseRating {
    @Id
    @GeneratedValue
    Long id
    @Relation(Relation.Kind.MANY_TO_ONE)
    Student student
    @Relation(Relation.Kind.MANY_TO_ONE)
    Course course
    int rating
}

@MappedEntity("m2m_course_rating_ck")
class CourseRatingCompositeKey {
    @EmbeddedId
    CourseRatingKey id

    int rating
}

@EqualsAndHashCode
@Embeddable
class CourseRatingKey {
    @MappedProperty("xyz_student_id")
    @Relation(Relation.Kind.MANY_TO_ONE)
    Student student
    @MappedProperty("abc_course_id")
    @Relation(Relation.Kind.MANY_TO_ONE)
    Course course
}

// @MapsId not supported yet
//@MappedEntity("m2m_course_rating_e")
//class CourseRatingWithEmbeddedId {
//    @EmbeddedId
//    CourseRatingKey id
//    @Relation(Relation.Kind.MANY_TO_ONE)
////    @MapsId("studentId")
////    @JoinColumn(name = "student_id")
//    Student student
//    @Relation(Relation.Kind.MANY_TO_ONE)
////    @MapsId("courseId")
////    @JoinColumn(name = "course_id")
//    @MappedProperty("unused")
//    Course course
//    int rating
//}

//@MappedEntity("m2m_course_rating_e")
//class CourseRatingWithEmbeddedId {
//    @EmbeddedId
//    CourseRatingKey id
//    @Relation(Relation.Kind.MANY_TO_ONE)
////    @MapsId("studentId")
////    @JoinColumn(name = "student_id")
//    Student student
//    @Relation(Relation.Kind.MANY_TO_ONE)
////    @MapsId("courseId")
////    @JoinColumn(name = "course_id")
//    @MappedProperty("unused")
//    Course course
//    int rating
//}

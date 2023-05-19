package io.micronaut.data.document.mongodb

import groovy.transform.EqualsAndHashCode
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.EmbeddedId
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Join
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.document.model.query.builder.MongoQueryBuilder
import io.micronaut.data.model.query.QueryModel
import io.micronaut.data.model.query.QueryParameter
import io.micronaut.data.model.query.builder.QueryBuilder
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.mongodb.annotation.MongoRepository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest
class MongoManyToManySpec extends Specification implements MongoTestPropertyProvider {
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

    def cleanup() {
        studentRepository.deleteAll()
        courseRepository.deleteAll()
        courseRatingRepository.deleteAll()
        courseRatingCompositeKeyRepository.deleteAll()
    }

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
//            student.ratings[0].student == student
            student.ratings[0].student == null
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
//            student.ratingsCK[0].id.student == student
            student.ratingsCK[0].id.student == null
            student.ratingsCK[0].id.course.name == "Physics"
            student.ratingsCK[0].rating == 5
    }

    void "test build Student select with courses"() {
        when:
            QueryBuilder encoder = new MongoQueryBuilder()
            def queryModel = QueryModel.from(getRuntimePersistentEntity(Student))
            queryModel.join("courses", Join.Type.FETCH, null)
            def q = encoder.buildQuery(queryModel.idEq(new QueryParameter("id")))
        then:
            q.query == '''[{$lookup:{from:'student_course',localField:'_id',foreignField:'m2m_student',pipeline:[{$lookup:{from:'m2m_course',localField:'m2m_course',foreignField:'_id',as:'m2m_course'}},{$unwind:{path:'$m2m_course',preserveNullAndEmptyArrays:true}},{$replaceRoot:{newRoot:'$m2m_course'}}],as:'courses'}},{$match:{_id:{$eq:{$mn_qp:0}}}}]'''
    }

    void "test build Student select with ratings"() {
        when:
            QueryBuilder encoder = new MongoQueryBuilder()
            def queryModel = QueryModel.from(getRuntimePersistentEntity(Student))
            queryModel.join("ratings", Join.Type.FETCH, null)
            def q = encoder.buildQuery(queryModel.idEq(new QueryParameter("id")))
        then:
            q.query == '''[{$lookup:{from:'m2m_course_rating',localField:'_id',foreignField:'student._id',as:'ratings'}},{$match:{_id:{$eq:{$mn_qp:0}}}}]'''
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

@MongoRepository
interface StudentRepository extends CrudRepository<Student, String> {

    @Join(value = "courses")
    @Override
    Optional<Student> findById(String id)

    @Join(value = "courses")
    @Join(value = "ratings")
    @Join(value = "ratings.course")
    Optional<Student> queryById(String id)

    @Join(value = "courses")
    @Join(value = "ratingsCK")
    @Join(value = "ratingsCK.id.course")
    Optional<Student> findByIdEquals(String id)

    int countDistinctByCoursesRatingsRatingInList(List<Integer> ratings);

}

@MongoRepository
interface CourseRepository extends CrudRepository<Course, String> {

    @Join(value = "students", type = Join.Type.LEFT_FETCH)
    @Override
    List<Course> findAll()
}

@MongoRepository
interface CourseRatingRepository extends CrudRepository<CourseRating, String> {

    @Join(value = "student", type = Join.Type.LEFT_FETCH)
    @Join(value = "course", type = Join.Type.LEFT_FETCH)
    @Override
    List<CourseRating> findAll()

    @Join(value = "student", type = Join.Type.LEFT_FETCH)
    @Join(value = "course", type = Join.Type.LEFT_FETCH)
    @Override
    Optional<CourseRating> findById(String id)
}

@MongoRepository
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
    String id
    String name
    @Relation(value = Relation.Kind.MANY_TO_MANY, cascade = Relation.Cascade.PERSIST)
    List<Course> courses
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "student")
    Set<CourseRating> ratings
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "id.student")
    Set<CourseRatingCompositeKey> ratingsCK
}

@EqualsAndHashCode(includes = "id")
@MappedEntity("m2m_course")
class Course {
    @Id
    @GeneratedValue
    String id
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
    String id
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
//    @MappedProperty("xyz_student_id")
    @Relation(Relation.Kind.MANY_TO_ONE)
    Student student
//    @MappedProperty("abc_course_id")
    @Relation(Relation.Kind.MANY_TO_ONE)
    Course course
}

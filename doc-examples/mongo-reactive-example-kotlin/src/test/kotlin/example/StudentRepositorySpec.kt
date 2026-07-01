package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StudentRepositorySpec : AbstractMongoSpec() {

    @Inject
    lateinit var studentRepository: StudentRepository

    @Inject
    lateinit var courseRatingRepository: CourseRatingRepository

    @Test
    fun testCrud() {
        assertNotNull(studentRepository)
        var student = Student("Denis", listOf(Course("Math"), Course("Physics")))
        student = studentRepository.save(student)
        assertNotNull(student.id)
        assertNotNull(student.version)
        assertEquals(0, student.version)
        var courses = requireNotNull(student.courses)
        assertNotNull(courses[0].id)
        assertNotNull(courses[1].id)
        studentRepository.update(student);
        student = studentRepository.findById(student.id!!).get()
        assertNotNull(student.id)
        assertEquals(1, student.version)
        assertEquals("Denis", student.name)
        courses = requireNotNull(student.courses)
        assertNotNull(courses[0].id)
        assertEquals("Math", courses[0].name)
        assertNotNull(courses[1].id)
        assertEquals("Physics", courses[1].name)
        val rating = CourseRating(student, courses[1], 5)
        courseRatingRepository.save(rating)
        student = studentRepository.queryById(student.id!!).orElse(null)
        assertNotNull(student.id)
        assertEquals("Denis", student.name)
        courses = requireNotNull(student.courses)
        assertNotNull(courses[0].id)
        assertEquals("Math", courses[0].name)
        assertNotNull(courses[1].id)
        assertEquals("Physics", courses[1].name)
        val ratings = requireNotNull(student.ratings)
        assertEquals(1, ratings.size)
        val (id, student1, course, rating1) = ratings.iterator().next()
        assertNotNull(id)
        assertEquals(student.id!!, student1.id)
        assertEquals("Physics", course.name)
        assertEquals(5, rating1)
    }
}

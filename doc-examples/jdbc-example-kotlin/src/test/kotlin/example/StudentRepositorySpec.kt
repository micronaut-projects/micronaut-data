package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@MicronautTest
class StudentRepositorySpec(private val studentRepository: StudentRepository,
                            private val courseRatingRepository: CourseRatingRepository) {

    @Test
    fun testCrud() {
        assertNotNull(studentRepository)
        var student = Student("Denis", listOf(Course("Math"), Course("Physics")))
        student = studentRepository.save(student)
        assertNotNull(student.id)
        assertNotNull(student.courses[0].id)
        assertNotNull(student.courses[1].id)
        student = studentRepository.findById(student.id).get()
        assertNotNull(student.id)
        assertEquals("Denis", student.name)
        assertNotNull(student.courses)
        assertNotNull(student.courses[0].id)
        assertEquals("Math", student.courses[0].name)
        assertNotNull(student.courses[1].id)
        assertEquals("Physics", student.courses[1].name)
        val rating = CourseRating(student, student.courses[1], 5)
        courseRatingRepository.save(rating)
        student = studentRepository.queryById(student.id!!).orElse(null)
        assertNotNull(student.id)
        assertEquals("Denis", student.name)
        assertNotNull(student.courses)
        assertNotNull(student.courses[0].id)
        assertEquals("Math", student.courses[0].name)
        assertNotNull(student.courses[1].id)
        assertEquals("Physics", student.courses[1].name)
        assertNotNull(student.ratings)
        assertEquals(1, student.ratings.size)
        val (id, student1, course, rating1) = student.ratings.iterator().next()
        assertNotNull(id)
        assertEquals(student.id!!, student1.id)
        assertEquals("Physics", course.name)
        assertEquals(5, rating1)
    }
}

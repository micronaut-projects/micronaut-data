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
        val student = studentRepository.save(Student("Denis", listOf(Course("Math"), Course("Physics"))))
        assertNotNull(student.id)
        assertNotNull(student.version)
        assertEquals(0, student.version)
        assertNotNull(student.courses[0].id)
        assertNotNull(student.courses[1].id)
        studentRepository.update(student)
        val student2 = studentRepository.findById(student.id!!)
        assertNotNull(student2!!.id)
        assertEquals(1, student2.version)
        assertEquals("Denis", student2.name)
        assertNotNull(student2.courses)
        assertNotNull(student2.courses[0].id)
        assertEquals("Math", student2.courses[0].name)
        assertNotNull(student2.courses[1].id)
        assertEquals("Physics", student2.courses[1].name)
        val rating = CourseRating(student2, student2.courses[1], 5)
        courseRatingRepository.save(rating)
        val student3 = studentRepository.queryById(student2.id!!)
        assertNotNull(student3!!.id)
        assertEquals("Denis", student3.name)
        assertNotNull(student3.courses)
        assertNotNull(student3.courses[0].id)
        assertEquals("Math", student3.courses[0].name)
        assertNotNull(student3.courses[1].id)
        assertEquals("Physics", student3.courses[1].name)
        assertNotNull(student3.ratings)
        assertEquals(1, student3.ratings.size)
        val (id, student1, course, rating1) = student3.ratings.iterator().next()
        assertNotNull(id)
        assertEquals(student3.id!!, student1.id)
        assertEquals("Physics", course.name)
        assertEquals(5, rating1)
    }
}

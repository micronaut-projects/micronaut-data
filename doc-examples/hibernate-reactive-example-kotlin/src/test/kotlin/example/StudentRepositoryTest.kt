package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.*

import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import jakarta.persistence.OptimisticLockException

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StudentRepositoryTest : PostgresHibernateReactiveProperties {

    @Inject
    lateinit var studentRepository: StudentRepository

    @Test
    fun testUpdateOptimisticLock(): Unit = runBlocking {
        try {
            studentRepository.testUpdateOptimisticLock(Student("Denis"))
        } catch (e: OptimisticLockException) {
            return@runBlocking
        }
        fail()
    }

    @Test
    fun testDeleteOptimisticLock(): Unit = runBlocking {
        try {
            studentRepository.testDeleteOptimisticLock(Student("Denis"))
        } catch (e: OptimisticLockException) {
            return@runBlocking
        }
        fail()
    }

    @Test
    fun testMergeOptimisticLock(): Unit = runBlocking {
        try {
            studentRepository.testMergeOptimisticLock(Student("Denis"))
        } catch (e: OptimisticLockException) {
            return@runBlocking
        }
        fail()
    }

}

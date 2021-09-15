package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import jakarta.inject.Inject

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SomeEntityRepositoryTest() : AbstractTest() {
    @Inject
    lateinit var repository: SomeEntityRepository

    @Test
    fun testInsertImmutableWithNullValue() {
        val result = repository.save(SomeEntity(null, null)).block()

        assertNotNull(result)
        assertNotNull(result?.id)
    }

}
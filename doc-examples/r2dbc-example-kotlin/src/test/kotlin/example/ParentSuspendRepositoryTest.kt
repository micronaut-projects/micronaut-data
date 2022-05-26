package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.TestInstance

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ParentSuspendRepositoryTest : AbstractTest(false) {

    @Inject
    private lateinit var repository: ParentSuspendRepository

    @Test
    internal fun `save parent with children`() = runBlocking {
        val children = mutableListOf<Child>()
        val parent = Parent("parent", children)
        children.addAll(
            arrayOf(
                Child("A", parent),
                Child("B", parent),
                Child("C", parent)
            )
        )
        val saved = repository.save(parent)
        assertNotNull(saved.id)
        println(saved.id)
        saved.children.forEach { assertNotNull(it.id) }

        val found = repository.findById(saved.id!!).get()
        found.children.forEach { assertNotNull(it.parent) }
        println("findById")

        val modifiedParent = found.copy(name = found.name + " mod!")
        repository.update(modifiedParent)
        val found2 = repository.findById(saved.id!!).get()
        assertTrue(found2.name.endsWith(" mod!"))
        assertTrue(found2.children.size == 3)
    }
}

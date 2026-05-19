package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Test
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.TestInstance

@MicronautTest(transactional = false)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ParentSuspendRepositoryTest : AbstractMongoSpec() {

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
        val savedChildren = requireNotNull(saved.children)
        savedChildren.forEach { assertNotNull(it.id) }
        assertEquals(3, savedChildren.size)

        val found = repository.findById(saved.id!!).get()
        val foundChildren = requireNotNull(found.children)
        foundChildren.forEach { assertNull(it.parent) }
        assertEquals(3, foundChildren.size)

        val modifiedParent = found.copy(name = found.name + " mod!")
        repository.update(modifiedParent)
        val found2 = repository.findById(saved.id!!).get()
        assertTrue(found2.name.endsWith(" mod!"))
        assertEquals(3, requireNotNull(found2.children).size)

    }
}

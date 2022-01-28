package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Test
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.TestInstance

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ParentRepositoryTest : AbstractMongoSpec() {

    @Inject
    private lateinit var repository: ParentRepository

    @Test
    internal fun `save parent with children`() {
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
        saved.children.forEach { assertNotNull(it.id) }

        val found = repository.findById(saved.id!!).get()
        found.children.forEach { assertNull(it.parent) }

        val modifiedParent = found.copy(name = found.name + " mod!")
        repository.update(modifiedParent)
        val found2 = repository.findById(saved.id!!).get()
        assertTrue(found2.name.endsWith(" mod!"))
        assertTrue(found2.children.size == 3)
    }
}

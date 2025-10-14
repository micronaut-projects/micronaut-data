package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import jakarta.inject.Inject

@MicronautTest
class ParentRepositoryTest {

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
        println(saved)
        assertNotNull(saved.id)
        saved.children.forEach { assertNotNull(it.id) }

        val found = repository.findById(saved.id!!)!!
        println(found)
        found.children.forEach { assertNotNull(it.parent) }

        val modifiedParent = found.copy(name = found.name + " mod!")
        repository.update(modifiedParent)
        val found2 = repository.findById(saved.id!!)!!
        assertTrue(found2.name.endsWith(" mod!"))
        assertTrue(found2.children.size == 3)
    }
}

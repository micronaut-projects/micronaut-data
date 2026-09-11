package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull

@MicronautTest(transactional = false)
class CompositeIdTest {

    @Inject
    lateinit var projectRepository: ProjectRepository

    @AfterEach
    fun cleanup() {
        projectRepository.deleteAll()
    }

    @Test
    fun testCompositeIdSave() {
        val projectId = ProjectId(1, 100)
        val project = Project(projectId, "Project Alpha")

        projectRepository.save(project)

        assertNotNull(project.projectId)
        assertEquals(1, project.projectId!!.departmentId)
        assertEquals(100, project.projectId!!.projectNumber)
    }

    @Test
    fun testCompositeIdFind() {
        val projectId1 = ProjectId(1, 100)
        val project1 = Project(projectId1, "Project Alpha")

        val projectId2 = ProjectId(2, 200)
        val project2 = Project(projectId2, "Project Beta")

        projectRepository.saveAll(listOf(project1, project2))

        // Find by composite ID
        val found = projectRepository.findById(projectId1).orElse(null)
        assertNotNull(found)
        assertEquals("Project Alpha", found!!.name)
        assertEquals(1, found.projectId!!.departmentId)
        assertEquals(100, found.projectId!!.projectNumber)
    }

    @Test
    fun testCompositeIdMultipleProjects() {
        // Save projects one at a time
        val projectId1 = ProjectId(10, 100)
        val project1 = Project(projectId1, "Project Alpha")
        projectRepository.save(project1)

        // Verify count
        assertEquals(1, projectRepository.count().toInt())

        // Find the project
        val found1 = projectRepository.findById(projectId1).orElse(null)
        assertNotNull(found1)
        assertEquals("Project Alpha", found1!!.name)
    }

    @Test
    fun testFindByName() {
        val projectId = ProjectId(1, 100)
        val project = Project(projectId, "Project Alpha")

        projectRepository.save(project)

        // Find by name
        val found = projectRepository.findByName("Project Alpha").orElse(null)
        assertNotNull(found)
        assertEquals("Project Alpha", found!!.name)
        assertEquals(1, found.projectId!!.departmentId)
        assertEquals(100, found.projectId!!.projectNumber)
    }

    @Test
    fun testCompositeIdDelete() {
        val projectId = ProjectId(1, 100)
        val project = Project(projectId, "Project Alpha")

        projectRepository.save(project)

        // Delete by composite ID
        projectRepository.deleteById(projectId)

        // Verify deletion
        val deleted = projectRepository.findById(projectId).orElse(null)
        assertNull(deleted)
    }
}

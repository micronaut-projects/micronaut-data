package example;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(transactional = false)
class CompositeIdSpec {

    @Inject
    ProjectRepository projectRepository;

    @AfterEach
    void cleanup() {
        projectRepository.deleteAll();
    }

    @Test
    void testCompositeIdSave() {
        ProjectId projectId = new ProjectId(1, 100);
        Project project = new Project(projectId, "Project Alpha");

        projectRepository.save(project);

        assertNotNull(project.getProjectId());
        assertEquals(1, project.getProjectId().getDepartmentId());
        assertEquals(100, project.getProjectId().getProjectNumber());
    }

    @Test
    void testCompositeIdFind() {
        ProjectId projectId1 = new ProjectId(1, 100);
        Project project1 = new Project(projectId1, "Project Alpha");

        ProjectId projectId2 = new ProjectId(2, 200);
        Project project2 = new Project(projectId2, "Project Beta");

        projectRepository.saveAll(java.util.List.of(project1, project2));

        // Find by composite ID
        Project found = projectRepository.findById(projectId1).orElse(null);
        assertNotNull(found);
        assertEquals("Project Alpha", found.getName());
        assertEquals(1, found.getProjectId().getDepartmentId());
        assertEquals(100, found.getProjectId().getProjectNumber());
    }

    @Test
    void testCompositeIdMultipleProjects() {
        // Save projects one at a time
        ProjectId projectId1 = new ProjectId(10, 100);
        Project project1 = new Project(projectId1, "Project Alpha");
        projectRepository.save(project1);

        // Verify count
        assertEquals(1, projectRepository.count());

        // Find the project
        Project found1 = projectRepository.findById(projectId1).orElse(null);
        assertNotNull(found1);
        assertEquals("Project Alpha", found1.getName());
    }

    @Test
    void testFindByName() {
        ProjectId projectId = new ProjectId(1, 100);
        Project project = new Project(projectId, "Project Alpha");

        projectRepository.save(project);

        // Find by name
        Project found = projectRepository.findByName("Project Alpha").orElse(null);
        assertNotNull(found);
        assertEquals("Project Alpha", found.getName());
        assertEquals(1, found.getProjectId().getDepartmentId());
        assertEquals(100, found.getProjectId().getProjectNumber());
    }

    @Test
    void testCompositeIdDelete() {
        ProjectId projectId = new ProjectId(1, 100);
        Project project = new Project(projectId, "Project Alpha");

        projectRepository.save(project);

        // Delete by composite ID
        projectRepository.deleteById(projectId);

        // Verify deletion
        Project deleted = projectRepository.findById(projectId).orElse(null);
        assertNull(deleted);
    }
}

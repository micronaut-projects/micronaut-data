package io.micronaut.data.hibernate.querygroupby

import io.micronaut.context.annotation.Property
import io.micronaut.data.hibernate.entities.MicronautProject
import io.micronaut.data.hibernate.entities.MicronautTask
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.PendingFeature
import spock.lang.Specification

import java.time.LocalDate

@MicronautTest(startApplication = false, packages = "io.micronaut.data.hibernate.entities")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class TaskRepositorySpec extends Specification {

    @Inject
    MicronautProjectRepository projectRepository

    @Inject
    MicronautTaskRepository taskRepository

    @PendingFeature
    void "@Query projecting to a POJO"() {
        given:
        MicronautProject p1 = projectRepository.save(new MicronautProject("P1", "Project 1", "Description of Project 1"))
        MicronautProject p2 = projectRepository.save(new MicronautProject("P2", "Project 2", "Description of Project 2"))
        MicronautProject p3 = projectRepository.save(new MicronautProject("P3", "Project 3", "Description of Project 3"))
        MicronautTask t1 = taskRepository.save(new MicronautTask("Task 1", "Task 1 Description", LocalDate.of(2025, 1, 12), p1, TaskStatus.TO_DO))
        MicronautTask t2 = taskRepository.save(new MicronautTask("Task 2", "Task 2 Description", LocalDate.of(2025, 2, 10), p1, TaskStatus.TO_DO))
        MicronautTask t3 = taskRepository.save(new MicronautTask("Task 3", "Task 3 Description", LocalDate.of(2025, 3, 16), p1, TaskStatus.TO_DO))
        MicronautTask t4 = taskRepository.save(new MicronautTask("Task 4", "Task 4 Description", LocalDate.of(2025, 6, 25), p1, TaskStatus.IN_PROGRESS))

        expect:
        3 == countIterableElements(projectRepository.findAll())

        4 == countIterableElements(taskRepository.findAll())

        when:
        Iterable<TasksPerYear> tasks = taskRepository.countByDueYear()

        then:
        2 == countIterableElements(tasks)

        cleanup:
        taskRepository.delete(t4)
        taskRepository.delete(t3)
        taskRepository.delete(t2)
        taskRepository.delete(t1)
        projectRepository.delete(p3)
        projectRepository.delete(p2)
        projectRepository.delete(p1)
    }

    @PendingFeature
    void "@Query returning List of List"() {
        given:
        MicronautProject p1 = projectRepository.save(new MicronautProject("P1", "Project 1", "Description of Project 1"))
        MicronautProject p2 = projectRepository.save(new MicronautProject("P2", "Project 2", "Description of Project 2"))
        MicronautProject p3 = projectRepository.save(new MicronautProject("P3", "Project 3", "Description of Project 3"))
        MicronautTask t1 = taskRepository.save(new MicronautTask("Task 1", "Task 1 Description", LocalDate.of(2025, 1, 12), p1, TaskStatus.TO_DO))
        MicronautTask t2 = taskRepository.save(new MicronautTask("Task 2", "Task 2 Description", LocalDate.of(2025, 2, 10), p1, TaskStatus.TO_DO))
        MicronautTask t3 = taskRepository.save(new MicronautTask("Task 3", "Task 3 Description", LocalDate.of(2025, 3, 16), p1, TaskStatus.TO_DO))
        MicronautTask t4 = taskRepository.save(new MicronautTask("Task 4", "Task 4 Description", LocalDate.of(2025, 6, 25), p1, TaskStatus.IN_PROGRESS))

        expect:
        3 == countIterableElements(projectRepository.findAll())

        4 == countIterableElements(taskRepository.findAll())
        when:
        List<List<Integer>> tasks = taskRepository.countByDueYearReturnList()

        then:
        2 == tasks.size()
        tasks.every { it.size()  == 2 }

        cleanup:
        taskRepository.delete(t4)
        taskRepository.delete(t3)
        taskRepository.delete(t2)
        taskRepository.delete(t1)
        projectRepository.delete(p3)
        projectRepository.delete(p2)
        projectRepository.delete(p1)
    }

    static int countIterableElements(Iterable<?> iterable) {
        int count = 0
        Iterator<?> iterator = iterable.iterator()
        if (iterator.hasNext()) {
            do {
                iterator.next()
                count++
            } while (iterator.hasNext())
        }
        count
    }
}

package example

import jakarta.inject.Inject

/**
 * This class exists to provide documentation snippets (it is not executed as a test).
 */
class ProjectRepositoryExample {

    @Inject lateinit var repository: ProjectRepository

    fun useEmbeddedId() {
        val id = ProjectId(10, 20)
        repository.save(Project(id, "Alpha"))
        repository.findById(id)
        repository.deleteById(id)
    }
}


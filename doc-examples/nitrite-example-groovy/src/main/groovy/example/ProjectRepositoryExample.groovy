package example

import jakarta.inject.Inject

/**
 * This class exists to provide documentation snippets (it is not executed as a test).
 */
class ProjectRepositoryExample {

    @Inject ProjectRepository repository

    // tag::useEmbeddedId[]
    void useEmbeddedId() {
        def id = new ProjectId(10, 20)
        repository.save(new Project(projectId: id, name: "Alpha"))
        repository.findById(id)
        repository.deleteById(id)
    }
    // end::useEmbeddedId[]
}


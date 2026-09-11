package example;

import jakarta.inject.Inject;

/**
 * This class exists to provide documentation snippets (it is not executed as a test).
 */
final class ProjectRepositoryExample {

    @Inject ProjectRepository repository;

    // tag::useEmbeddedId[]
    void useEmbeddedId() {
        ProjectId id = new ProjectId(10, 20);
        repository.save(new Project(id, "Alpha"));
        repository.findById(id);
        repository.deleteById(id);
    }
    // end::useEmbeddedId[]
}


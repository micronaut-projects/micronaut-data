package example

import io.micronaut.data.annotation.EmbeddedId
import io.micronaut.data.annotation.MappedEntity

// tag::project[]
@MappedEntity("projects")
class Project {
    @EmbeddedId
    var projectId: ProjectId? = null
    
    var name: String = ""
    
    constructor()
    
    constructor(projectId: ProjectId, name: String) {
        this.projectId = projectId
        this.name = name
    }
}
// end::project[]

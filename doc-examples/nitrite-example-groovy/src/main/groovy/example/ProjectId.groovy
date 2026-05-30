package example

import io.micronaut.data.annotation.Embeddable

// tag::projectId[]
@Embeddable
class ProjectId {
    final int departmentId
    final int projectNumber

    ProjectId(int departmentId, int projectNumber) {
        this.departmentId = departmentId
        this.projectNumber = projectNumber
    }
}
// end::projectId[]


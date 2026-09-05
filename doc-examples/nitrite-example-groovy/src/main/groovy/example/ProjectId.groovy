package example

import io.micronaut.data.annotation.Embeddable
import io.micronaut.core.annotation.Introspected

import java.io.Serializable

// tag::projectId[]
@Embeddable
@Introspected
class ProjectId implements Serializable {
    final int departmentId
    final int projectNumber

    ProjectId(int departmentId, int projectNumber) {
        this.departmentId = departmentId
        this.projectNumber = projectNumber
    }
}
// end::projectId[]

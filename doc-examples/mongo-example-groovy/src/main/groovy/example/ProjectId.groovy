
package example

import groovy.transform.EqualsAndHashCode
import io.micronaut.data.annotation.Embeddable

@EqualsAndHashCode
@Embeddable
class ProjectId {
    final int departmentId
    final int projectId

    ProjectId(int departmentId, int projectId) {
        this.departmentId = departmentId
        this.projectId = projectId
    }
}

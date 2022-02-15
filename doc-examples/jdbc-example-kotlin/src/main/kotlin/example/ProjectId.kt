
package example

import jakarta.persistence.Embeddable

@Embeddable
data class ProjectId(val departmentId: Int, val projectId: Int)
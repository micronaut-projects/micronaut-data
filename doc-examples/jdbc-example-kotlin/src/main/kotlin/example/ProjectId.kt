
package example

import javax.persistence.Embeddable

@Embeddable
data class ProjectId(val departmentId: Int, val projectId: Int)
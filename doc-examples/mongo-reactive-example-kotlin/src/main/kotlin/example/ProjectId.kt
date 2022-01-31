
package example

import io.micronaut.data.annotation.Embeddable

@Embeddable
data class ProjectId(val departmentId: Int, val projectId: Int)
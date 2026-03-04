package example

import io.micronaut.data.annotation.Embeddable

// tag::projectId[]
@Embeddable
data class ProjectId(val departmentId: Int, val projectNumber: Int)
// end::projectId[]


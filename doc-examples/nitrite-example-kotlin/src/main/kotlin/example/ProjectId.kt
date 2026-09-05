package example

import io.micronaut.core.annotation.Introspected
import io.micronaut.data.annotation.Embeddable
import java.io.Serializable

// tag::projectId[]
@Embeddable
@Introspected
data class ProjectId(val departmentId: Int, val projectNumber: Int) : Serializable
// end::projectId[]

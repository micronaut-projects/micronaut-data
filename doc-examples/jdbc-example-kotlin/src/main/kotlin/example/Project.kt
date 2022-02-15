
package example

import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity

@Entity
class Project(
    @EmbeddedId val projectId: ProjectId,
    val name: String
)

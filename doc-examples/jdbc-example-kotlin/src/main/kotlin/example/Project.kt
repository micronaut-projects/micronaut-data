
package example

import javax.persistence.EmbeddedId
import javax.persistence.Entity

@Entity
class Project(
    @EmbeddedId val projectId: ProjectId,
    val name: String
)

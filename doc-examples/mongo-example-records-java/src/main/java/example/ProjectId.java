
package example;

import io.micronaut.data.annotation.Embeddable;

@Embeddable
public record ProjectId(
        int departmentId,
        int projectId) {
}

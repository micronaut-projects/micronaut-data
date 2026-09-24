from dataclasses import dataclass

from micronaut.data.annotation import Embeddable


@Embeddable
@dataclass(frozen=True)
class ProjectId:
    departmentId: int
    projectId: int

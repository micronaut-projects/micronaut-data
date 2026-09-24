from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import EmbeddedId, MappedEntity

from example.ProjectId import ProjectId


@MappedEntity
@dataclass
class Project:
    projectId: Annotated[ProjectId, EmbeddedId]
    name: str

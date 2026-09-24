from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import Embeddable, Id, JsonSubView, MappedProperty

from example.Teacher import Teacher


@Embeddable
# tag::class-example[]
@JsonSubView(entity=Teacher)
@dataclass
class TeacherSubView:
    teachID: Annotated[int | None, Id, MappedProperty(value="id")] = None
    teacher: Annotated[str | None, MappedProperty(value="name")] = None
# end::class-example[]

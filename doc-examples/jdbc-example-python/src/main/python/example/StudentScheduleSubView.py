from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import Id, JsonSubView

from example.StudentClass import StudentClass


# tag::class-example[]
@JsonSubView(entity=StudentClass, operations=["UPDATE", "INSERT"])
@dataclass
class StudentScheduleSubView:
    # end::class-example[]
    id: Annotated[int | None, Id] = None

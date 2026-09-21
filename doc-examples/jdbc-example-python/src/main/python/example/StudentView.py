from dataclasses import dataclass, field
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, JsonView, Relation
from micronaut.data.annotation.sql import JoinColumn

from example.Student import Student
from example.StudentScheduleSubView import StudentScheduleSubView


# tag::class-example[]
@JsonView(entity=Student)
@dataclass
class StudentView:
    name: str
    schedule: Annotated[list[StudentScheduleSubView], JoinColumn(name="id", referencedColumnName="student_id"), Relation("ONE_TO_MANY")] = field(default_factory=list)
    id: Annotated[int | None, Id, GeneratedValue("IDENTITY")] = None
# end::class-example[]

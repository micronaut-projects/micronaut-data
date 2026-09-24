import base64
from dataclasses import dataclass
from java.time import MonthDay
from typing import Annotated

from jakarta.persistence import Column, Convert, Entity, GeneratedValue, Id, PrePersist

from example.MonthDayDateAttributeConverter import MonthDayDateAttributeConverter


@Entity
@dataclass
class Account:
    username: str | None = None
    password: str | None = None
    paymentDay: Annotated[MonthDay | None, Column(columnDefinition="date"), Convert(converter=MonthDayDateAttributeConverter)] = None
    id: Annotated[int | None, Id, GeneratedValue] = None

    @PrePersist
    def encode_password(self) -> None:
        self.password = base64.b64encode(self.password.encode("utf-8")).decode("ascii")

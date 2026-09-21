from dataclasses import dataclass
from typing import Annotated

from jakarta.persistence import Entity, GeneratedValue, Id
from micronaut.configuration.hibernate.jpa.proxy import GenerateProxy
from org.hibernate.annotations import BatchSize


@Entity
@GenerateProxy
@BatchSize(size=10)
@dataclass
class Manufacturer:
    name: str | None = None
    id: Annotated[int | None, Id, GeneratedValue] = None

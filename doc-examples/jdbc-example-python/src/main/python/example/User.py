from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity, Where
from micronaut.data.model.naming import NamingStrategies


@MappedEntity(value="users", namingStrategy=NamingStrategies.Raw)
@Where("@.userEnabled = true")  # <1>
@dataclass
class User:
    userName: str
    userEnabled: bool = True  # <2>
    id: Annotated[int | None, Id, GeneratedValue] = None

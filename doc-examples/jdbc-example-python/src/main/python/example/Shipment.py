from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity


@MappedEntity(value="TBL_SHIPMENT", alias="sh")
@dataclass
class Shipment:
    reference: str
    country: str
    trackingCode: str
    weight: float | None = None
    rate: float | None = None
    id: Annotated[int | None, Id, GeneratedValue("IDENTITY")] = None

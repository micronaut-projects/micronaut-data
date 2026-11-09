from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, JsonView, JsonViewField, JsonViewGenerated

from example.Shipment import Shipment


# tag::view[]
@JsonView(
    value="EU_SHIPMENT_VIEW",
    entity=Shipment,
    where="@.country IN ('FR', 'DE', 'IT')",  # <1>
    hidden=["weight", "rate"],  # <2>
)
@dataclass
class ShipmentView:
    reference: str
    country: str
    trackingCode: Annotated[str, JsonViewField(updatable=False)]  # <3>
    cost: Annotated[float | None, JsonViewGenerated(path="$.weight * $.rate")] = None  # <4>
    label: Annotated[str | None, JsonViewGenerated(sql="UPPER(@.reference)")] = None  # <5>
    id: Annotated[int | None, Id, GeneratedValue("IDENTITY")] = None
# end::view[]

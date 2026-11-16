from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import Id, MappedEntity, Sharded


# tag::root[]
@Sharded(tablespaceSet="TS_SUBSCRIBERS")  # <1>
@MappedEntity
@dataclass
class Subscriber:
    subscriberId: Annotated[str, Id]  # <2>
    name: str
# end::root[]

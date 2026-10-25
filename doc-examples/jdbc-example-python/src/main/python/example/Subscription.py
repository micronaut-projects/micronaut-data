from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import EmbeddedId, MappedEntity, Sharded

from example.Subscriber import Subscriber
from example.SubscriptionId import SubscriptionId


# tag::child[]
@Sharded(parent=Subscriber, tablespaceSet="TS_SUBSCRIBERS")  # <1>
@MappedEntity
@dataclass
class Subscription:
    id: Annotated[SubscriptionId, EmbeddedId]  # <2>
    plan: str
# end::child[]

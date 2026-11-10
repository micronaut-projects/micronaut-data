from dataclasses import dataclass

from micronaut.data.annotation import Embeddable


@Embeddable
@dataclass(frozen=True)
class SubscriptionId:
    subscriberId: str
    subscriptionNo: int

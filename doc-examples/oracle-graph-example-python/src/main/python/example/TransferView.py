# tag::view[]
from dataclasses import dataclass

from java.math import BigDecimal
from micronaut.core.annotation import Introspected


@Introspected
@dataclass
class TransferView:
    source: str
    target: str
    amount: BigDecimal
# end::view[]

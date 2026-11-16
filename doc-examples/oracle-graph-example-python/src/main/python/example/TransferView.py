# tag::view[]
from dataclasses import dataclass

from java.math import BigDecimal
from micronaut.serde.annotation import Serdeable


@Serdeable
@dataclass
class TransferView:
    source: str
    target: str
    amount: BigDecimal
# end::view[]

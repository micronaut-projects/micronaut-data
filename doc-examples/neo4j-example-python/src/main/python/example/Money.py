from dataclasses import dataclass

from java.math import BigDecimal


@dataclass
class Money:
    amount: BigDecimal
    currency: str

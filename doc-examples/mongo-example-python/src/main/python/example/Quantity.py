from dataclasses import dataclass


@dataclass(frozen=True)
class Quantity:
    amount: int

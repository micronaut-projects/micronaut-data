from dataclasses import dataclass


@dataclass
class GenderAware:
    gender: str | None = None

from typing import Annotated

from jakarta.validation import Valid
from jakarta.validation.constraints import Min
from micronaut.data.annotation import Repository
from micronaut.data.repository import CrudRepository

from example.Account import Account


@Repository
class AccountRepository(CrudRepository[Annotated[Account, Valid], Annotated[int, Min(0)]]):
    pass

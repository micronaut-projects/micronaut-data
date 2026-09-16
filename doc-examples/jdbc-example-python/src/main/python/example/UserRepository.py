from micronaut.data.annotation import Query
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.model.query.builder.sql import Dialect
from micronaut.data.repository import CrudRepository

from example.User import User


@JdbcRepository(dialect=Dialect.H2)
class UserRepository(CrudRepository[User, int]):  # <1>

    @Query("UPDATE users SET userEnabled = false WHERE id = :id")  # <2>
    def deleteById(self, id: int | None) -> None: ...

    @Query("SELECT * FROM users WHERE userEnabled = false")  # <3>
    def findDisabled(self) -> list[User]: ...

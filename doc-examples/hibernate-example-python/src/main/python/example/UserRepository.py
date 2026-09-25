from java.util.stream import Stream
from micronaut.data.annotation import Fetch
from micronaut.data.annotation import Query, Repository
from micronaut.data.repository import CrudRepository

from example.User import User


@Repository
class UserRepository(CrudRepository[User, int]):

    @Query("UPDATE Users SET enabled = false WHERE id = :id")
    def deleteById(self, id: int) -> None: ...

    @Query("FROM Users WHERE enabled = false")
    def findDisabled(self) -> list[User]: ...

    # tag::fetch_size_streaming[]
    @Fetch(1500)
    def listAll(self) -> Stream[User]:
        """Retrieves all users from the data store in batches.

        The returned Stream is lazily evaluated, meaning that it will only fetch data as it is consumed.
        The fetch size is set to 1500, which controls the number of rows fetched from the database at a time.
        It is the caller's responsibility to close the Stream to release any underlying resources.

        :return: a Stream of all enabled users
        """
        ...

    def queryAll(self) -> Stream[User]:
        """Retrieves all users from the data store.

        The returned Stream is lazily evaluated, meaning that it will only fetch data as it is consumed.
        It is the caller's responsibility to close the Stream to release any underlying resources.

        :return: a Stream of all users
        """
        ...
    # end::fetch_size_streaming[]

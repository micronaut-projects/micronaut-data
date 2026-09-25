from micronaut.data.annotation import Where

from example.User import User


class UserRepositoryWithWhere:

    # ...

    @Where("@.enabled = false")
    def findDisabled(self) -> list[User]: ...

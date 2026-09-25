from typing import Annotated

from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.User import User
from example.UserRepository import UserRepository


@MicronautTest
class UserRepositorySpec:

    userRepository: Annotated[UserRepository, Inject]

    @Test
    def testSoftDelete(self):
        fred, bob, joe = self.userRepository.saveAll([
            User("Fred"),
            User("Bob"),
            User("Joe"),
        ])

        self.userRepository.deleteById(joe.id)

        assert self.userRepository.count() == 2
        assert self.userRepository.existsById(fred.id)
        assert not self.userRepository.existsById(joe.id)

        disabled = self.userRepository.findDisabled()
        assert len(disabled) == 1
        assert disabled[0].userName == "Joe"

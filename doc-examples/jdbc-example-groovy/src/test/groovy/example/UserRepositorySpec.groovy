package example

import io.micronaut.test.annotation.MicronautTest
import spock.lang.Specification

import javax.inject.Inject

@MicronautTest
class UserRepositorySpec extends Specification {
    @Inject UserRepository userRepository

    void 'test soft delete'() {
        given:'Some users'
        final User joe = new User("Joe")
        final User fred = new User("Fred")
        final User bob = new User("Bob")
        userRepository.saveAll(Arrays.asList(
                fred,
                bob,
                joe
        ))

        when:"A user is disabled"
        userRepository.deleteById(joe.getId())

        then:"It is not returned any any queries"
        userRepository.count() == 2
        userRepository.existsById(fred.id)
        !userRepository.existsById(joe.id)

        when:"disabled users are queried"
        final List<User> disabled = userRepository.findDisabled()

        then:"Only disabled users are returned"
        disabled.size() == 1
        disabled.first().name == 'Joe'
    }
}

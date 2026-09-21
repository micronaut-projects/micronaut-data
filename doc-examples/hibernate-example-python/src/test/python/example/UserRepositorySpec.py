from typing import Annotated

from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.transaction import TransactionOperations
from org.hibernate import Session
from org.junit.jupiter.api import AfterEach, BeforeEach, Test

from example.User import User
from example.UserRepository import UserRepository


@MicronautTest
class UserRepositorySpec:

    userRepository: Annotated[UserRepository, Inject]
    transactionOperations: Annotated[TransactionOperations[Session], Inject]

    @BeforeEach
    def setup(self):
        self.userRepository.deleteAll()

    @AfterEach
    def tearDown(self):
        self.userRepository.deleteAll()

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
        assert disabled[0].name == "Joe"

    @Test
    def testStreaming(self):
        total = 500_000
        self.seed_users(total)

        # tag::stream_and_count[]
        def count_users(status):
            session = status.getConnection()
            # Execute query with annotated FetchSize
            stream = self.userRepository.listAll()
            try:
                count = 0
                for user in stream.iterator():
                    session.detach(user)
                    count += 1
                    if count % 50_000 == 0:
                        session.clear()
                return count
            finally:
                stream.close()

        entity_count = self.transactionOperations.executeRead(count_users)
        # end::stream_and_count[]
        assert entity_count == total

        def count_all_users(status):
            session = status.getConnection()
            # Execute query with default fetch size
            stream = self.userRepository.queryAll()
            try:
                count = 0
                for user in stream.iterator():
                    session.detach(user)
                    count += 1
                return count
            finally:
                stream.close()

        entity_count = self.transactionOperations.executeRead(count_all_users)
        assert entity_count == total

    def seed_users(self, count: int):
        sql = """
            INSERT INTO users(id, name, enabled)
                   SELECT x, 'Name ' || x, true
                   FROM SYSTEM_RANGE(0, ?);
        """

        # Execute via Hibernate Session to avoid requiring @Connectable on DataSource
        def seed(status):
            session = status.getConnection()
            query = session.createNativeMutationQuery(sql)
            query.setParameter(1, count - 1)
            query.executeUpdate()
            return None

        self.transactionOperations.executeWrite(seed)

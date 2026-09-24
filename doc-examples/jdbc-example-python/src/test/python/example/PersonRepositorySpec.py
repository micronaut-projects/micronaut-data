from typing import Annotated

from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import AfterEach, BeforeEach, Test

from example.Person import Person
from example.PersonRepository import PersonRepository, age_is_less_than, and_, long_name_equals, name_equals, not_, or_, set_new_name


@MicronautTest
class PersonRepositorySpec:

    personRepository: Annotated[PersonRepository, Inject]

    @BeforeEach
    def beforeEach(self):
        self.personRepository.saveAll([
            Person("Denis", 13, "Denis The Team Lead"),
            Person("Josh", 22, "Josh PM"),
        ])

    @AfterEach
    def afterEach(self):
        self.personRepository.deleteAll()

    @Test
    def testFind(self):
        # tag::find[]
        denis = self.personRepository.findOne(name_equals("Denis")).orElse(None)

        josh = self.personRepository.findOne(long_name_equals("Josh PM")).orElse(None)

        count_age_less_30 = self.personRepository.count(age_is_less_than(30))

        count_age_less_20 = self.personRepository.count(age_is_less_than(20))

        count_age_less_30_not_denis = self.personRepository.count(and_(age_is_less_than(30), not_(name_equals("Denis"))))

        people = self.personRepository.findAll(or_(name_equals("Denis"), name_equals("Josh")))
        # end::find[]

        assert denis is not None
        assert denis.longName == "Denis The Team Lead"
        assert josh is not None
        assert josh.longName == "Josh PM"
        assert count_age_less_30 == 2
        assert count_age_less_20 == 1
        assert count_age_less_30_not_denis == 1
        assert len(people) == 2

    @Test
    def testDelete(self):
        all = self.personRepository.findAll(None)
        assert len(all) == 2

        # tag::delete[]
        records_deleted = self.personRepository.deleteAll(name_equals("Denis"))
        # end::delete[]

        assert records_deleted == 1

        all = self.personRepository.findAll(None)
        assert len(all) == 1

    @Test
    def testUpdate(self):
        all = self.personRepository.findAll(None)
        assert len(all) == 2
        assert any(p.name == "Denis" for p in all)
        assert any(p.name == "Josh" for p in all)

        # tag::update[]
        records_updated = self.personRepository.updateAll(set_new_name("Steven", where=name_equals("Denis")))
        # end::update[]

        assert records_updated == 1

        all = self.personRepository.findAll(None)
        assert len(all) == 2
        assert any(p.name == "Steven" for p in all)
        assert any(p.name == "Josh" for p in all)

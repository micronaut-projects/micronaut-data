from typing import Annotated

from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import AfterEach, Test

from example.Pet import Pet
from example.PetRepository import PetRepository


@MicronautTest(transactional=False)
class PetRepositorySpec:

    petRepository: Annotated[PetRepository, Inject]

    @AfterEach
    def afterEach(self):
        self.petRepository.deleteAll()

    @Test
    def testSchemaless(self):
        # tag::schemaless[]
        dino = Pet("Dino")
        dino.attributes["goesInPatch"] = True  # <1>
        dino.attributes["legs"] = 4
        self.petRepository.save(dino)

        found = self.petRepository.findById("Dino").get()
        goes_in_patch = found.attributes["goesInPatch"]  # <2>
        in_patch = self.petRepository.findByGoesInPatch(True)  # <3>
        # end::schemaless[]

        assert goes_in_patch is True
        assert [p.name for p in in_patch] == ["Dino"]

    @Test
    def testDynamicRelationships(self):
        # tag::dynamicRelationships[]
        dino = Pet("Dino")
        dino.related["PLAYS_WITH"] = [Pet("Hoppy")]  # <1>
        self.petRepository.save(dino)  # <2>

        found = self.petRepository.findById("Dino").get()
        plays_with = found.related["PLAYS_WITH"]  # <3>
        # end::dynamicRelationships[]

        assert [p.name for p in plays_with] == ["Hoppy"]

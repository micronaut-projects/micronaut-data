from typing import Annotated

from jakarta.inject import Inject
from micronaut.data.model.vector import BinaryVector
from micronaut.data.model.vector.search import ScoringFunction
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.ImageFingerprint import ImageFingerprint
from example.ImageFingerprintRepository import ImageFingerprintRepository


@MicronautTest(transactional=False)
class ImageFingerprintRepositorySpec:

    repository: Annotated[ImageFingerprintRepository, Inject]

    @Test
    def searchBinaryVectors(self):
        self.repository.deleteAll()
        # tag::binary_vector[]
        sunset = BinaryVector.of(*[True] * 8, *[False] * 8)  # one bool per bit
        sunset_cropped = BinaryVector.of(*[True] * 8, *[False] * 7, True)
        # end::binary_vector[]
        forest = BinaryVector.of(*[False] * 8, *[True] * 8)
        self.repository.save(ImageFingerprint(None, "sunset", sunset))
        self.repository.save(ImageFingerprint(None, "sunset-cropped", sunset_cropped))
        self.repository.save(ImageFingerprint(None, "forest", forest))

        # tag::binary_search[]
        similar = self.repository.searchTop3ByHashNear(sunset, 4.0).results()
        # end::binary_search[]
        assert [r.entity().name for r in similar] == ["sunset", "sunset-cropped"]
        assert [r.score().value() for r in similar] == [0.0, 1.0]

        jaccard = self.repository.searchTop2ByHashNear(sunset, 1.0, ScoringFunction.JACCARD).results()
        assert jaccard[1].entity().name == "sunset-cropped"
        assert abs(jaccard[1].score().value() - 1.0 / 9.0) < 1e-6

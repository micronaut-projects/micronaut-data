package example

import io.micronaut.data.model.vector.BinaryVector
import io.micronaut.data.model.vector.search.ScoringFunction
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(transactional = false)
class ImageFingerprintRepositorySpec extends Specification {

    @Inject
    ImageFingerprintRepository repository

    void "search binary vectors"() {
        given:
        repository.deleteAll()
        // tag::binary_vector[]
        BinaryVector sunset = new BinaryVector([(byte) 0xFF, 0x00] as byte[]) // bits packed 8 per byte
        BinaryVector sunsetCropped = BinaryVector.of(
            true, true, true, true, true, true, true, true,
            false, false, false, false, false, false, false, true)
        // end::binary_vector[]
        BinaryVector forest = new BinaryVector([0x00, (byte) 0xFF] as byte[])
        repository.save(new ImageFingerprint(name: 'sunset', hash: sunset))
        repository.save(new ImageFingerprint(name: 'sunset-cropped', hash: sunsetCropped))
        repository.save(new ImageFingerprint(name: 'forest', hash: forest))

        when:
        // tag::binary_search[]
        def similar = repository.searchTop3ByHashNear(sunset, 4d).results()
        // end::binary_search[]
        def jaccard = repository.searchTop2ByHashNear(sunset, 1d, ScoringFunction.JACCARD).results()

        then:
        similar*.entity()*.name == ['sunset', 'sunset-cropped']
        similar*.score()*.value() == [0d, 1d]
        jaccard[1].entity().name == 'sunset-cropped'
        Math.abs(jaccard[1].score().value() - 1d / 9d) < 1e-6d
    }
}

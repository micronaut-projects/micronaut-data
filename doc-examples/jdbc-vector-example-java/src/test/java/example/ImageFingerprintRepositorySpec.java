package example;

import io.micronaut.data.model.vector.BinaryVector;
import io.micronaut.data.model.vector.search.ScoringFunction;
import io.micronaut.data.model.vector.search.SearchResult;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest(transactional = false)
class ImageFingerprintRepositorySpec {

    @Inject
    ImageFingerprintRepository repository;

    @Test
    void searchBinaryVectors() {
        repository.deleteAll();
        // tag::binary_vector[]
        BinaryVector sunset = new BinaryVector(new byte[] {(byte) 0xFF, 0x00}); // bits packed 8 per byte
        BinaryVector sunsetCropped = BinaryVector.of(
            true, true, true, true, true, true, true, true,
            false, false, false, false, false, false, false, true);
        // end::binary_vector[]
        BinaryVector forest = new BinaryVector(new byte[] {0x00, (byte) 0xFF});
        repository.save(new ImageFingerprint(null, "sunset", sunset));
        repository.save(new ImageFingerprint(null, "sunset-cropped", sunsetCropped));
        repository.save(new ImageFingerprint(null, "forest", forest));

        // tag::binary_search[]
        List<SearchResult<ImageFingerprint>> similar = repository.searchTop3ByHashNear(sunset, 4d).results();
        // end::binary_search[]
        assertEquals(List.of("sunset", "sunset-cropped"), similar.stream().map(r -> r.entity().name()).toList());
        assertEquals(List.of(0d, 1d), similar.stream().map(r -> r.score().value()).toList());

        List<SearchResult<ImageFingerprint>> jaccard = repository.searchTop2ByHashNear(sunset, 1d, ScoringFunction.JACCARD).results();
        assertEquals("sunset-cropped", jaccard.get(1).entity().name());
        assertEquals(1d / 9d, jaccard.get(1).score().value(), 1e-6d);
    }
}

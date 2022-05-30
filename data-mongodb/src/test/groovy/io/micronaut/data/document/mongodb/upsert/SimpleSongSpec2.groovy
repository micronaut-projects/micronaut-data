package io.micronaut.data.document.mongodb.upsert

import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.document.mongodb.upsert.model.SongEntity2
import io.micronaut.data.document.mongodb.upsert.repo.SongRepository2
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.bson.types.ObjectId
import org.junit.Test
import spock.lang.Specification

/**
 * The overall goal is to use Strings as keys and:
 * to write to the repository without having to read the entity first
 * (insert if not exist, always overwrite if same id exists)
 *
 * This version uses ObjectIds (which we would rather not use because our
 * ids have 32 bytes while ObjectIds only allow 24)
 *
 */
@MicronautTest(transactional = false)
class SimpleSongSpec2 extends Specification implements MongoTestPropertyProvider {

    @Inject
    private SongRepository2 songRepository

    @Test
    void savesAndRetrievesSongs() {
        when:
            ObjectId id = new ObjectId("ffffffffffffffffffffffff")
            SongEntity2 songEntity = new SongEntity2()
            songEntity.setId(id)
            songEntity.setName("Don't Worry, be happy")

            songRepository.save(songEntity)
        then:
            Optional<SongEntity2> song1 = songRepository.findById(id)
            song1.isPresent()
    }

    @Test
    void updatesSongs() {
        when:
            ObjectId id = new ObjectId("eeeeeeeeeeeeeeeeeeeeeeee")

            SongEntity2 songEntity = new SongEntity2()
            songEntity.setId(id)
            songEntity.setName("Don't Worry, be happy")

            songRepository.update(songEntity)
        then:
            Optional<SongEntity2> song2 = songRepository.findById(id)
            song2.isPresent()
    }

}

package io.micronaut.data.document.mongodb.upsert

import io.micronaut.data.document.mongodb.MongoTestPropertyProvider
import io.micronaut.data.document.mongodb.upsert.model.SongEntity
import io.micronaut.data.document.mongodb.upsert.repo.SongRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.junit.Test
import spock.lang.Specification

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * The overall goal is to use Strings as keys and:
 * to write to the repository without having to read the entity first
 * (insert if not exist, always overwrite if same id exists)
 */
@MicronautTest(transactional = false)
class SimpleSongSpec extends Specification implements MongoTestPropertyProvider {

    @Inject
    private SongRepository songRepository

    @Test
    void savesAndRetrievesSongs() {
        when:
            SongEntity songEntity = new SongEntity()
            songEntity.setSongHash("song_1")
            songEntity.setName("Don't Worry, be happy")
            songRepository.save(songEntity)
            LocalDateTime created = songEntity.getCreated().truncatedTo(ChronoUnit.MILLIS)
            LocalDateTime updated = songEntity.getUpdated().truncatedTo(ChronoUnit.MILLIS)
        then:
            SongEntity song1 = songRepository.findById("song_1").get()
            song1.getSongHash() == "song_1"

        when:
            def updatedSong = songRepository.update(song1)
            def updatedSong2 = songRepository.findById("song_1").get()
        then:
            updatedSong.created == created
            updatedSong.updated != updated
            updatedSong.updated == song1.updated
            updatedSong2.created == created
            updatedSong2.updated != updated
            updatedSong2.updated == updatedSong.updated.truncatedTo(ChronoUnit.MILLIS)
    }

    @Test
    void updatesSongs() {
        when:
            SongEntity songEntity = new SongEntity()
            songEntity.setSongHash("song_2")
            songEntity.setName("Don't Worry, be happy")

            songRepository.update(songEntity)
        then:
            Optional<SongEntity> song2 = songRepository.findById("song_2")
            song2.isPresent()
    }
}

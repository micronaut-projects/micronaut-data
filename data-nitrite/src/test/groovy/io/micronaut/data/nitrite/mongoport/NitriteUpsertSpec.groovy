package io.micronaut.data.nitrite.mongoport

import io.micronaut.data.nitrite.mongoport.entities.NitriteSong
import io.micronaut.data.nitrite.mongoport.repositories.NitriteSongRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.time.LocalDateTime

@MicronautTest(transactional = false)
class NitriteUpsertSpec extends Specification implements NitriteTestPropertyProvider {

    @Inject
    private NitriteSongRepository songRepository

    void "saves and retrieves songs"() {
        when:
            NitriteSong songEntity = new NitriteSong()
            songEntity.setSongHash("song_1")
            songEntity.setName("Don't Worry, be happy")
            songEntity.setCreated(LocalDateTime.now())
            songEntity.setUpdated(LocalDateTime.now())
            songRepository.save(songEntity)
            LocalDateTime created = songEntity.getCreated()
        then:
            NitriteSong song1 = songRepository.findById("song_1").get()
            song1.getSongHash() == "song_1"
            song1.getName() == "Don't Worry, be happy"

        when:
            song1.setUpdated(LocalDateTime.now())
            NitriteSong updatedSong = songRepository.update(song1)
            NitriteSong updatedSong2 = songRepository.findById("song_1").get()
        then:
            updatedSong.created == created
            updatedSong.updated != created
            updatedSong2.updated != created
    }

    void "updates songs"() {
        when:
            NitriteSong songEntity = new NitriteSong()
            songEntity.setSongHash("song_2")
            songEntity.setName("Don't Worry, be happy")
            songEntity.setCreated(LocalDateTime.now())
            songEntity.setUpdated(LocalDateTime.now())

            songRepository.save(songEntity)
        then:
            Optional<NitriteSong> song2 = songRepository.findById("song_2")
            song2.isPresent()
    }
}

package io.micronaut.data.graph.runtime.serde

import io.micronaut.context.ApplicationContext
import io.micronaut.data.graph.runtime.entities.Album
import io.micronaut.serde.ObjectMapper
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Serde caches the deserializer it builds at runtime for a type, with the property deserializers of the first context
 * to ask for it. Graph reads and JSON of the same class must not see each other's.
 */
class GraphSerdeIsolationSpec extends Specification {

    static final String JSON = '{"title":"Kid A","released":"2000-10-02","recorded":"2000-04-01T10:00:00Z"}'
    static final Map<String, Object> NODE = [
        title   : 'Kid A',
        released: LocalDate.of(2000, 10, 2),
        // A driver returns its own temporal type, which is converted to that of the property rather than read as text
        recorded: ZonedDateTime.of(2000, 4, 1, 12, 0, 0, 0, ZoneId.of('Europe/Paris'))
    ]

    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    ObjectMapper json = context.getBean(ObjectMapper)
    GraphSerde serde = context.getBean(GraphSerde)

    void "JSON reads a class after a graph read of it"() {
        when:
        Album fromNode = serde.read(Album, NODE)
        Album fromJson = json.readValue(JSON, Album)

        then:
        verify(fromNode)
        verify(fromJson)
    }

    void "a graph read reads a class after JSON read it"() {
        when:
        Album fromJson = json.readValue(JSON, Album)
        Album fromNode = serde.read(Album, NODE)

        then:
        verify(fromJson)
        verify(fromNode)
    }

    private static boolean verify(Album album) {
        assert album.title == 'Kid A'
        assert album.released == LocalDate.of(2000, 10, 2)
        assert album.recorded == Instant.parse('2000-04-01T10:00:00Z')
        return true
    }
}

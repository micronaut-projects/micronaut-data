package example.notification

import io.micronaut.context.annotation.Requires
import io.micronaut.data.jdbc.annotation.ChangeListener
import io.micronaut.data.jdbc.annotation.OracleChangeNotification
import io.micronaut.data.jdbc.notification.ChangeEvent
import io.micronaut.data.jdbc.notification.ChangeOperation
import io.micronaut.data.jdbc.notification.oracle.OracleChangeEventMetadata
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.ConcurrentHashMap

@Requires(property = "query-notification.object.enabled")
// tag::listener[]
@Singleton
class LibraryCache {
    // end::listener[]
    private static final Logger LOG = LoggerFactory.getLogger(LibraryCache)

    private final LibraryRepository repository
    private volatile Map<Long, Library> libraries = new ConcurrentHashMap<>()

    LibraryCache(LibraryRepository repository) {
        this.repository = repository
    }

    @PostConstruct
    void initialize() {
        refreshCache()
    }

    Optional<Library> find(String name) {
        libraries.values().stream()
            .filter { library -> library.name() == name }
            .findFirst()
    }

    // tag::listener[]
    // tag::events[]
    @ChangeListener
    @OracleChangeNotification
    synchronized void onLibraryChanged(ChangeEvent<Library> event) {
        // end::listener[]
        event.metadata(OracleChangeEventMetadata).ifPresent { metadata ->
            LOG.debug("Changed library ROWID: {}", metadata.rowId())
        }
        switch (event.operation()) {
            case ChangeOperation.INSERT:
            case ChangeOperation.UPDATE:
                event.entity().ifPresent { library -> libraries.put(library.id(), library) }
                break
            case ChangeOperation.DELETE:
            case ChangeOperation.INVALIDATE:
                refreshCache()
                break
        }
        // tag::listener[]
    }
    // end::listener[]
    // end::events[]

    private synchronized void refreshCache() {
        def currentLibraries = repository.findAll()
        Map<Long, Library> refreshed = new ConcurrentHashMap<>()
        currentLibraries.forEach { library -> refreshed.put(library.id(), library) }
        libraries = refreshed
    }
    // tag::listener[]
}
// end::listener[]

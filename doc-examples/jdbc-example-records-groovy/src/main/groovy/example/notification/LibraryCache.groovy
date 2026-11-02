package example.notification

import io.micronaut.context.annotation.Requires
import io.micronaut.data.annotation.ChangeListener
import io.micronaut.data.jdbc.annotation.OracleChangeNotification
import io.micronaut.data.notification.ChangeEvent
import io.micronaut.data.notification.ChangeOperation
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
    private volatile boolean cacheEnabled = true

    LibraryCache(LibraryRepository repository) {
        this.repository = repository
    }

    @PostConstruct
    void initialize() {
        refreshCache()
    }

    Optional<Library> find(String name) {
        def currentLibraries = cacheEnabled ? libraries.values() : repository.findAll()
        currentLibraries.stream()
            .filter { library -> library.name() == name }
            .findFirst()
    }

    // tag::listener[]
    // tag::events[]
    @ChangeListener
    @OracleChangeNotification
    synchronized void onLibraryChanged(ChangeEvent<Library> event) {
        // end::listener[]
        if (!cacheEnabled) {
            return
        }
        event.metadata(OracleChangeEventMetadata).ifPresent { metadata ->
            LOG.debug("Changed library table: {}.{}, ROWID: {}",
                metadata.schema().orElse("<unknown>"), metadata.table(), metadata.rowId())
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
            case ChangeOperation.TERMINATED:
                cacheEnabled = false
                libraries.clear()
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

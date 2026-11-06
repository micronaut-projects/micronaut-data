package example.notification;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.annotation.ChangeListener;
import io.micronaut.data.jdbc.annotation.OracleChangeNotification;
import io.micronaut.data.notification.ChangeEvent;
import io.micronaut.data.jdbc.notification.oracle.OracleChangeEventMetadata;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Requires(property = "query-notification.object.enabled")
// tag::listener[]
@Singleton
class LibraryCache {
    // end::listener[]
    private static final Logger LOG = LoggerFactory.getLogger(LibraryCache.class);

    private final LibraryRepository repository;
    private volatile Map<Long, Library> libraries = new ConcurrentHashMap<>();
    private volatile boolean cacheEnabled = true;

    LibraryCache(LibraryRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    void initialize() {
        refreshCache();
    }

    public Optional<Library> find(String name) {
        var currentLibraries = cacheEnabled ? libraries.values() : repository.findAll();
        return currentLibraries
            .stream()
            .filter(library -> library.name().equals(name))
            .findFirst();
    }

    // tag::listener[]
    // tag::listener-signature[]
    @ChangeListener // <1>
    // end::listener-signature[]
    @OracleChangeNotification // <2>
    // tag::events[]
    // tag::listener-signature[]
    synchronized void onLibraryChanged(ChangeEvent<Library> event) {
        // end::listener-signature[]
        // end::listener[]
        if (!cacheEnabled) {
            return;
        }
        event.metadata(OracleChangeEventMetadata.class) // <1>
            .ifPresent(metadata -> LOG.debug("Changed library table: {}.{}, ROWID: {}",
                Objects.requireNonNullElse(metadata.schema(), "<unknown>"), metadata.table(), metadata.rowId()));
        switch (event.operation()) {
            case INSERT, UPDATE -> event.entity().ifPresent(library -> libraries.put(library.id(), library)); // <2>
            case DELETE, INVALIDATE -> refreshCache(); // <3>
            case TERMINATED -> {
                cacheEnabled = false; // <4>
                libraries.clear();
            }
        }
        // tag::listener[]
    // tag::listener-signature[]
    }
    // end::listener-signature[]
    // end::listener[]
    // end::events[]

    private synchronized void refreshCache() {
        var currentLibraries = repository.findAll();
        Map<Long, Library> refreshed = new ConcurrentHashMap<>();
        currentLibraries.forEach(library -> refreshed.put(library.id(), library));
        libraries = refreshed;
    }
    // tag::listener[]
}
// end::listener[]

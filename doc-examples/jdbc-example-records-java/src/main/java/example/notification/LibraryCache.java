package example.notification;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.jdbc.annotation.ChangeListener;
import io.micronaut.data.jdbc.annotation.OracleChangeNotification;
import io.micronaut.data.jdbc.notification.ChangeEvent;
import io.micronaut.data.jdbc.notification.oracle.OracleChangeEventMetadata;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
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

    LibraryCache(LibraryRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    void initialize() {
        refreshCache();
    }

    public Optional<Library> find(String name) {
        return libraries.values()
            .stream()
            .filter(library -> library.name().equals(name))
            .findFirst();
    }

    // tag::listener[]
    // tag::events[]
    @ChangeListener
    @OracleChangeNotification
    synchronized void onLibraryChanged(ChangeEvent<Library> event) {
        // end::listener[]
        event.metadata(OracleChangeEventMetadata.class)
            .ifPresent(metadata -> LOG.debug("Changed library ROWID: {}", metadata.rowId()));
        switch (event.operation()) {
            case INSERT, UPDATE -> event.entity().ifPresent(library -> libraries.put(library.id(), library));
            case DELETE, INVALIDATE -> refreshCache();
        }
        // tag::listener[]
    }
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

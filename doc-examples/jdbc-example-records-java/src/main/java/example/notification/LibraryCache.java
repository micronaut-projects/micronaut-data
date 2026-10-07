package example.notification;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.data.jdbc.annotation.ChangeListener;
import io.micronaut.data.jdbc.annotation.OracleChangeNotification;
import io.micronaut.data.jdbc.notification.ChangeEvent;
import io.micronaut.data.jdbc.notification.oracle.OracleChangeEventMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// tag::listener[]
@Context
@Requires(property = "query-notification.object.enabled")
class LibraryCache implements ApplicationEventListener<StartupEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(LibraryCache.class);

    private final LibraryRepository repository;
    private final Map<Long, Library> libraries = new ConcurrentHashMap<>();

    LibraryCache(LibraryRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        refreshCache();
    }

    public Optional<Library> find(String name) {
        return libraries.values()
            .stream()
            .filter(library -> library.name().equals(name))
            .findFirst();
    }

    // tag::events[]
    // tag::datasource[]
    @ChangeListener(dataSource = "default")
    // end::datasource[]
    @OracleChangeNotification
    synchronized void onLibraryChanged(ChangeEvent<Library> event) {
        event.metadata(OracleChangeEventMetadata.class)
            .ifPresent(metadata -> LOG.debug("Changed library ROWID: {}", metadata.rowId()));
        switch (event.operation()) {
            case INSERT, UPDATE -> event.entity().ifPresent(library -> libraries.put(library.id(), library));
            case DELETE, INVALIDATE -> refreshCache();
        }
    }
    // end::events[]

    private synchronized void refreshCache() {
        var currentLibraries = repository.findAll();
        libraries.clear();
        currentLibraries.forEach(library -> libraries.put(library.id(), library));
    }
}
// end::listener[]

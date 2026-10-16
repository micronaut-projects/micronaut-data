package example.notification;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.jdbc.annotation.ChangeListener;
import io.micronaut.data.jdbc.annotation.OracleChangeNotification;
import io.micronaut.data.jdbc.notification.ChangeEvent;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@Requires(property = "query-notification.query.enabled")
class LargeLibraryCache {

    private final LibraryRepository repository;
    private volatile Map<Long, Library> libraries = new ConcurrentHashMap<>();

    LargeLibraryCache(LibraryRepository repository) {
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

    // tag::query[]
    @ChangeListener
    @OracleChangeNotification(
        select = "name",
        where = "capacity >= 10000",
        properties = @OracleChangeNotification.Property(
            name = "DCN_QUERY_CHANGE_NOTIFICATION",
            value = "true"
        )
    )
    synchronized void onLibraryChanged(ChangeEvent<Library> event) {
        switch (event.operation()) {
            case INSERT, UPDATE -> event.entity().ifPresent(library -> {
                if (library.capacity() >= 10000) {
                    libraries.put(library.id(), library);
                } else {
                    libraries.remove(library.id());
                }
            });
            case DELETE, INVALIDATE -> refreshCache();
        }
    }
    // end::query[]

    private synchronized void refreshCache() {
        var currentLibraries = repository.findByCapacityGreaterThanEquals(10000);
        Map<Long, Library> refreshed = new ConcurrentHashMap<>();
        currentLibraries.forEach(library -> refreshed.put(library.id(), library));
        libraries = refreshed;
    }
}

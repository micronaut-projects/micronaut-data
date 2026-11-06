package example.notification;

import io.micronaut.context.annotation.Requires;
import io.micronaut.data.annotation.ChangeListener;
import io.micronaut.data.jdbc.annotation.OracleChangeNotification;
import io.micronaut.data.notification.ChangeEvent;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@Requires(property = "query-notification.query.enabled")
class LibraryNameCache {

    private final LibraryRepository repository;
    private volatile Map<Long, String> libraryNames = new ConcurrentHashMap<>();
    private volatile boolean cacheEnabled = true;

    LibraryNameCache(LibraryRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    void initialize() {
        refreshCache();
    }

    public Optional<String> find(long id) {
        return cacheEnabled ? Optional.ofNullable(libraryNames.get(id))
            : repository.findById(id).filter(library -> library.capacity() >= 10000).map(Library::name);
    }

    public boolean contains(String name) {
        return cacheEnabled ? libraryNames.containsValue(name)
            : repository.findByCapacityGreaterThanEquals(10000).stream().anyMatch(library -> library.name().equals(name));
    }

    // tag::query[]
    @ChangeListener
    @OracleChangeNotification(
        select = "id, name", // <1>
        where = "capacity >= 10000", // <2>
        properties = @OracleChangeNotification.Property(
            name = "DCN_QUERY_CHANGE_NOTIFICATION",
            value = "true" // <3>
        )
    )
    void onLibraryChanged(ChangeEvent<Library> event) {
        if (!cacheEnabled) {
            return;
        }
        switch (event.operation()) {
            case INSERT, UPDATE -> event.entity().ifPresent(library -> {
                if (library.capacity() >= 10000) {
                    libraryNames.put(library.id(), library.name());
                } else {
                    libraryNames.remove(library.id());
                }
            });
            case DELETE, INVALIDATE -> refreshCache();
            case TERMINATED -> {
                cacheEnabled = false;
                libraryNames.clear();
            }
        }
    }
    // end::query[]

    private void refreshCache() {
        Map<Long, String> refreshed = new ConcurrentHashMap<>();
        repository.findByCapacityGreaterThanEquals(10000).forEach(library -> refreshed.put(library.id(), library.name()));
        libraryNames = refreshed;
    }
}

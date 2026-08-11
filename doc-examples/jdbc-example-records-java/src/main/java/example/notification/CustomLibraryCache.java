package example.notification;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.data.jdbc.annotation.ChangeListener;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Context
@Requires(property = "query-notification.query.enabled")
final class CustomLibraryCache implements ApplicationEventListener<StartupEvent> {

    private final LibraryRepository repository;
    private final Map<Long, Library> libraries = new ConcurrentHashMap<>();

    CustomLibraryCache(LibraryRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onApplicationEvent(StartupEvent event) {
        repository.findByCapacityGreaterThanEquals(10000).forEach(library -> libraries.put(library.id(), library));
    }

    public Optional<Library> find(String name) {
        return libraries.values()
            .stream()
            .filter(library -> library.name().equals(name))
            .findFirst();
    }

    @ChangeListener(
        select = "name",
        where = "capacity >= 10000",
        properties = {
            @ChangeListener.Property(name = "DCN_CLIENT_INIT_CONNECTION", value = "true"),
            @ChangeListener.Property(name = "DCN_QUERY_CHANGE_NOTIFICATION", value = "true")
        }
    )
    void onLibraryChanged(Library library) {
        if (library.capacity() >= 10000) {
            libraries.put(library.id(), library);
        } else {
            libraries.remove(library.id());
        }
    }
}

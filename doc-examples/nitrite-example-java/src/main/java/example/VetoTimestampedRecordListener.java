package example;

import io.micronaut.data.event.EntityEventContext;
import io.micronaut.data.event.EntityEventListener;
import jakarta.inject.Singleton;

// tag::pre-persist-listener[]
@Singleton
public class VetoTimestampedRecordListener implements EntityEventListener<TimestampedRecord> {
    @Override
    public boolean prePersist(EntityEventContext<TimestampedRecord> context) {
        return !"veto-me".equals(context.getEntity().getName());
    }
}
// end::pre-persist-listener[]

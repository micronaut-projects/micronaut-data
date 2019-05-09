package io.micronaut.data.runtime.intercept;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.data.intercept.*;
import io.micronaut.data.store.Datastore;

/**
 * Factory for creating the different types of interceptors.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
public class PredatorInterceptorFactory {

    /**
     * Creates the {@link FindOneInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link FindOneInterceptor}
     */
    @EachBean(Datastore.class)
    protected FindOneInterceptor findOneInterceptor(Datastore datastore) {
        return new DefaultFindOneInterceptor(datastore);
    }

    /**
     * Creates the {@link FindOptionalInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link FindOptionalInterceptor}
     */
    @EachBean(Datastore.class)
    protected FindOptionalInterceptor findOptionalInterceptor(Datastore datastore) {
        return new DefaultFindOptionalInterceptor(datastore);
    }

    /**
     * Creates the {@link FindAllByInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link FindAllByInterceptor}
     */
    @EachBean(Datastore.class)
    protected FindAllByInterceptor findAllByInterceptor(Datastore datastore) {
        return new DefaultFindAllByInterceptor(datastore);
    }

    /**
     * Creates the {@link FindAllInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link FindAllInterceptor}
     */
    @EachBean(Datastore.class)
    protected FindAllInterceptor findAllInterceptor(Datastore datastore) {
        return new DefaultFindAllInterceptor(datastore);
    }

    /**
     * Creates the {@link DeleteByInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link DeleteByInterceptor}
     */
    @EachBean(Datastore.class)
    protected DeleteByInterceptor deleteByInterceptor(Datastore datastore) {
        return new DefaultDeleteByInterceptor(datastore);
    }

    /**
     * Creates the {@link DeleteAllInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link DeleteAllInterceptor}
     */
    @EachBean(Datastore.class)
    protected DeleteAllInterceptor deleteAllInterceptor(Datastore datastore) {
        return new DefaultDeleteAllInterceptor(datastore);
    }

    /**
     * Creates the {@link DeleteOneInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link DeleteOneInterceptor}
     */
    @EachBean(Datastore.class)
    protected DeleteOneInterceptor deleteOneInterceptor(Datastore datastore) {
        return new DefaultDeleteOneInterceptor(datastore);
    }

    /**
     * Creates the {@link CountAllInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link CountAllInterceptor}
     */
    @EachBean(Datastore.class)
    protected CountAllInterceptor countAllInterceptor(Datastore datastore) {
        return new DefaultCountInterceptor(datastore);
    }

    /**
     * Creates the {@link CountByInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link CountByInterceptor}
     */
    @EachBean(Datastore.class)
    protected CountByInterceptor countByInterceptor(Datastore datastore) {
        return new DefaultCountByInterceptor(datastore);
    }

    /**
     * Creates the {@link io.micronaut.data.intercept.SaveEntityInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link FindAllByInterceptor}
     */
    @EachBean(Datastore.class)
    protected SaveEntityInterceptor saveEntityInterceptor(Datastore datastore) {
        return new DefaultSaveEntityInterceptor(datastore);
    }

    /**
     * Creates the {@link io.micronaut.data.intercept.SaveAllInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link FindAllByInterceptor}
     */
    @EachBean(Datastore.class)
    protected SaveAllInterceptor saveAllInterceptor(Datastore datastore) {
        return new DefaultSaveAllInterceptor(datastore);
    }

    /**
     * Creates the {@link io.micronaut.data.intercept.ExistsByInterceptor} instances for each configured {@link Datastore}.
     *
     * @param datastore The datastore
     * @return The {@link FindAllByInterceptor}
     */
    @EachBean(Datastore.class)
    protected ExistsByInterceptor existsByInterceptor(Datastore datastore) {
        return new DefaultExistsByInterceptor(datastore);
    }
}

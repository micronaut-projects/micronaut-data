package io.micronaut.data.cql.operations;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.operations.RepositoryOperations;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractCqlRepositoryOperations<RS,PS> implements RepositoryOperations {


    private final Map<Class, RuntimePersistentEntity> entities = new ConcurrentHashMap<>(10);

    @NonNull
    @Override
    public <T> RuntimePersistentEntity<T> getEntity(@NonNull Class<T> type) {
        ArgumentUtils.requireNonNull("type", type);
        RuntimePersistentEntity<T> entity = entities.get(type);
        if (entity == null) {
            entity = new RuntimePersistentEntity<T>(type) {
                @Override
                protected RuntimePersistentEntity<T> getEntity(Class<T> type) {
                    return AbstractCqlRepositoryOperations.this.getEntity(type);
                }
            };
            entities.put(type, entity);
        }
        return entity;
    }

    public final <T> void setInsertParameters(@NonNull StoredInsert<T> insert, @NonNull T entity, @NonNull PS stmt) {
//        RuntimePersistentEntity<T> persistentEntity = insert.get
    }


    /**
     * A stored insert statement.
     *
     * @param <T> The entity type
     */
    protected final class StoredInsert<T> {
        private final String[] parameterBinding;
        private final String cql;
        private final RuntimePersistentEntity<T> persistentEntity;

        StoredInsert(
            String cql,
            RuntimePersistentEntity<T> persistentEntity,
            String[] parameterBinding
        ) {
            this.cql = cql;
            this.persistentEntity = persistentEntity;
            this.parameterBinding = parameterBinding;
        }

        public @NonNull
        String getCql() {return cql; }

        public @NonNull
        String[] getParameterBinding() {
            return parameterBinding;
        }

        public @NonNull
        RuntimePersistentEntity<T> getPersistentEntity() {
            return persistentEntity;
        }
    }


}

package io.micronaut.data.cql.runtime;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.annotation.cql.PartitionKey;
import io.micronaut.data.model.AbstractPersistentEntity;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CassandraRuntimePersistentEntity<T> extends AbstractPersistentEntity implements PersistentEntity {
    private final BeanIntrospection<T> introspection;
    private final Map<String,RuntimePersistentProperty<T>> paritionKeys;

    protected CassandraRuntimePersistentEntity(@NonNull Class<T> type) {
        this(BeanIntrospection.getIntrospection(type));
    }

    /**
     * Default constructor.
     * @param introspection The introspection
     */
    public CassandraRuntimePersistentEntity(@NonNull BeanIntrospection<T> introspection) {
        super(introspection);
        ArgumentUtils.requireNonNull("introspection", introspection);
        this.introspection = introspection;
        Argument<?>[] constructorArguments = introspection.getConstructorArguments();
        Set<String> constructorArgumentNames = Arrays.stream(constructorArguments).map(Argument::getName).collect(Collectors.toSet());
//        for(paritionKeys : introspection.getIndexedProperty(PartitionKey.class)){
//
//        }
        paritionKeys = new LinkedHashMap<>();
        Collection<BeanProperty<T, Object>> beanProperties = introspection.getBeanProperties();
        for (BeanProperty<T, Object> bp : beanProperties) {
            RuntimePersistentProperty<T> prop;
            if(bp.hasStereotype(PartitionKey.class)){
                prop = new RuntimePersistentProperty<>(this,bp,constructorArgumentNames.contains(bp.getName()));
                paritionKeys.put(prop.getName(),prop);
            }
        }
    }
    @NonNull
    @Override
    public String getName() {
        return null;
    }

    @Nullable
    @Override
    public PersistentProperty[] getCompositeIdentity() {
        return new PersistentProperty[0];
    }

    @Nullable
    @Override
    public PersistentProperty getIdentity() {
        return null;
    }

    @Nullable
    @Override
    public PersistentProperty getVersion() {
        return null;
    }

    @NonNull
    @Override
    public Collection<? extends PersistentProperty> getPersistentProperties() {
        return null;
    }

    @NonNull
    @Override
    public Collection<? extends Association> getAssociations() {
        return null;
    }

    @NonNull
    @Override
    public Collection<Embedded> getEmbedded() {
        return null;
    }

    @Nullable
    @Override
    public PersistentProperty getPropertyByName(String name) {
        return null;
    }

    @NonNull
    @Override
    public Collection<String> getPersistentPropertyNames() {
        return null;
    }

    @Override
    public boolean isOwningEntity(PersistentEntity owner) {
        return false;
    }

    @Nullable
    @Override
    public PersistentEntity getParentEntity() {
        return null;
    }
}

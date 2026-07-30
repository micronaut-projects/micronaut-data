package io.micronaut.data.nitrite.runtime.mapping;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;

import java.util.List;

/**
 * Per-property metadata pre-computed once per entity type. Read-only and {@code @Transient}
 * properties are excluded at build time and never appear here.
 * {@link PropertyStrategy#ASSOCIATION_MAPPED_BY} entries are excluded from
 * {@link NitriteEntityMeta#writableProps()} and appear only in
 * {@link NitriteEntityMeta#mappedByAssocs()}.
 *
 * @param <T>                   the entity type
 * @param prop                  the runtime persistent property
 * @param fieldName             the persisted field name
 * @param strategy              the serialization strategy
 * @param mappedBy              the mappedBy value for back-references (if applicable)
 * @param associatedIdProp      the ID property accessor for association references (if applicable)
 * @param backRefProperty       the back-reference property setter (if applicable)
 * @param compositeJoinColumns  local/referenced property name pairs when the association declares
 *                              more than one {@code @JoinColumn} (composite foreign key); empty otherwise
 */
public record WritablePropertyMeta<T>(
    RuntimePersistentProperty<T> prop,
    String fieldName,
    @Nullable PropertyStrategy strategy,
    @Nullable String mappedBy,
    @Nullable BeanProperty<Object, Object> associatedIdProp,
    @Nullable BeanProperty<Object, Object> backRefProperty,
    List<CompositeJoinColumn> compositeJoinColumns
) {
}

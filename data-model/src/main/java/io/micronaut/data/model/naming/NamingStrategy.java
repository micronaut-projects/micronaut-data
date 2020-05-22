/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.model.naming;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.Embedded;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;

import java.util.function.Supplier;


/**
 * A strategy interface for resolving the mapped name of an entity or property.
 *
 * @author graemerocher
 * @since 1.0
 */
@FunctionalInterface
@Introspected
public interface NamingStrategy {

    /**
     * Constant for the default under score separated lower case strategy.
     */
    NamingStrategy DEFAULT = new NamingStrategies.UnderScoreSeparatedLowerCase();

    /**
     * Return the mapped name for the given name.
     * @param name The name
     * @return The mapped name
     */
    @NonNull
    String mappedName(@NonNull String name);

    /**
     * Return the mapped name for the given entity.
     * @param entity The entity
     * @return The mapped name
     */
    default @NonNull String mappedName(@NonNull PersistentEntity entity) {
        ArgumentUtils.requireNonNull("entity", entity);
        return entity.getAnnotationMetadata().stringValue(MappedEntity.class)
                .filter(StringUtils::isNotEmpty)
                .orElseGet(() -> mappedName(entity.getSimpleName()));
    }

    /**
     * Return the mapped name given an {@link Embedded} association and the property of the assocation. The
     * default strategy takes the parent embedded property name and combines it underscore separated with the child parent property name.
     *
     * <p>For example given:</p>
     *
     * <pre><code>
     * {@literal @}Embedded Address address;
     * </code></pre>
     *
     * <p>Where the {@code Address} type has a property called {@code street} then a name of {@code address_street} will be returned</p>
     * @param embedded The embedded parent
     * @param property The embedded property
     * @return The mapped name
     */
    default @NonNull String mappedName(Embedded embedded, PersistentProperty property) {
        return mappedName(embedded.getName() + property.getCapitilizedName());
    }

    /**
     * Return the mapped name for the given property.
     * @param property The property
     * @return The mapped name
     */
    default @NonNull String mappedName(@NonNull PersistentProperty property) {
        ArgumentUtils.requireNonNull("property", property);
        Supplier<String> defaultNameSupplier = () -> mappedName(property.getName());
        if (property instanceof Association) {
            Association association = (Association) property;
            if (association.isForeignKey()) {
                return mappedName(association.getOwner().getDecapitalizedName() +
                                    association.getAssociatedEntity().getSimpleName());
            } else {
                switch (association.getKind()) {
                    case ONE_TO_ONE:
                    case MANY_TO_ONE:
                        return property.getAnnotationMetadata().stringValue(MappedProperty.class)
                                .orElseGet(() -> mappedName(property.getName() + getForeignKeySuffix()));
                    default:
                        return property.getAnnotationMetadata().stringValue(MappedProperty.class)
                                .orElseGet(defaultNameSupplier);
                }
            }
        } else {
            return property.getAnnotationMetadata().stringValue(MappedProperty.class)
                    .map(s -> StringUtils.isEmpty(s) ? defaultNameSupplier.get() : s)
                    .orElseGet(defaultNameSupplier);
        }
    }

    /**
     * The default foreign key suffix for property names.
     * @return The suffix
     */
    default @NonNull String getForeignKeySuffix() {
        return "Id";
    }
}

/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.runtime.intercept;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Default {@link EntityIdentityPresenceChecker} implementation.
 */
@Internal
final class DefaultEntityIdentityPresenceChecker implements EntityIdentityPresenceChecker {

    @Override
    public boolean hasIdentity(RuntimePersistentEntity<Object> persistentEntity, Object entity) {
        if (persistentEntity.hasIdentity()) {
            RuntimePersistentProperty<Object> identity = persistentEntity.getIdentity();
            return hasAssignedIdentityValue(identity, identity.getProperty().get(entity));
        }
        if (persistentEntity.hasCompositeIdentity()) {
            for (RuntimePersistentProperty<Object> identity : persistentEntity.getCompositeIdentity()) {
                if (!hasAssignedIdentityValue(identity, identity.getProperty().get(entity))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean hasGeneratedIdentity(RuntimePersistentEntity<Object> persistentEntity) {
        if (persistentEntity.hasIdentity()) {
            return isGeneratedIdentity(persistentEntity.getIdentity());
        }
        if (persistentEntity.hasCompositeIdentity()) {
            for (RuntimePersistentProperty<Object> identity : persistentEntity.getCompositeIdentity()) {
                if (!isGeneratedIdentity(identity)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean hasNonGeneratedNonNegativeIdentity(RuntimePersistentEntity<Object> persistentEntity, Object entity) {
        if (persistentEntity.hasIdentity()) {
            RuntimePersistentProperty<Object> identity = persistentEntity.getIdentity();
            return !isGeneratedIdentity(identity) && isNonNegativeIdentityValue(identity.getProperty().get(entity));
        }
        if (persistentEntity.hasCompositeIdentity()) {
            for (RuntimePersistentProperty<Object> identity : persistentEntity.getCompositeIdentity()) {
                if (isGeneratedIdentity(identity) || !isNonNegativeIdentityValue(identity.getProperty().get(entity))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private boolean isGeneratedIdentity(RuntimePersistentProperty<Object> identity) {
        return identity.isGenerated() || isAlwaysAutoPopulated(identity);
    }

    private boolean isAlwaysAutoPopulated(RuntimePersistentProperty<Object> identity) {
        return identity.isAutoPopulated()
            && !identity.getAnnotationMetadata().booleanValue(AutoPopulated.class, AutoPopulated.SKIP_IF_PRESENT).orElse(false);
    }

    private boolean hasAssignedIdentityValue(RuntimePersistentProperty<Object> identity, @Nullable Object value) {
        if (value == null) {
            return false;
        }
        return !isGeneratedIdentity(identity) || !(value instanceof Number number) || !isZero(number);
    }

    private boolean isNonNegativeIdentityValue(@Nullable Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Number number) {
            return !isNegative(number);
        }
        return true;
    }

    private boolean isNegative(Number number) {
        if (number instanceof BigDecimal bigDecimal) {
            return bigDecimal.signum() < 0;
        }
        if (number instanceof BigInteger bigInteger) {
            return bigInteger.signum() < 0;
        }
        return number.doubleValue() < 0D;
    }

    private boolean isZero(Number number) {
        if (number instanceof BigDecimal bigDecimal) {
            return bigDecimal.signum() == 0;
        }
        if (number instanceof BigInteger bigInteger) {
            return bigInteger.signum() == 0;
        }
        return number.doubleValue() == 0D;
    }
}

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
package io.micronaut.data.nitrite.runtime.write;

import io.micronaut.core.annotation.Internal;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.collection.UpdateOptions;
import org.dizitart.no2.filters.Filter;

/**
 * Collection-level primitives used by the Jakarta Data CRUD implementation.
 *
 * <p>An identity is resolved by one filter, because this provider stores an identity the Nitrite
 * document key can hold as that key and every other identity in the indexed identity field. No
 * operation here enables Nitrite's {@code insertIfAbsent} option: Jakarta Data {@code save}
 * resolves an insert from an update by looking the identity up first, and {@code update} never
 * inserts.</p>
 *
 * @since 5.2.0
 */
@Internal
final class NitriteCrudOperations {

    private NitriteCrudOperations() {
    }

    static boolean exists(NitriteCollection collection, Filter identityFilter) {
        return collection.find(identityFilter).firstOrNull() != null;
    }

    static long update(NitriteCollection collection,
                       Filter identityFilter,
                       Document document) {
        // Nitrite's WriteResult deliberately reports zero when the update document contains no
        // mutable fields (for example, an identity-only update), although the filter did match a
        // document. Jakarta Data CRUD counts the matched entity, so inspect the match separately.
        if (collection.find(identityFilter).firstOrNull() != null) {
            collection.update(identityFilter, document, UpdateOptions.updateOptions(false));
            return 1;
        }
        return 0;
    }

    static long remove(NitriteCollection collection, Filter identityFilter) {
        return collection.remove(identityFilter, false).getAffectedCount();
    }
}

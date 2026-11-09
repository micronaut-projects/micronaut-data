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
package io.micronaut.data.mongodb.operations;

import com.mongodb.MongoCommandException;
import io.micronaut.core.annotation.Internal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Whether the server runs {@code $rankFusion}. It does until an aggregation fails because the server does not
 * know the stage, or does not allow it at its feature compatibility version; the hybrid searches of the
 * repository operations are then fused in memory for the rest of their lifetime, and a warning is logged once.
 * Any other failure of a {@code $rankFusion} aggregation is not a reason to fall back and is rethrown.
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Internal
final class RankFusionAvailability {

    // "Unrecognized pipeline stage name: '$rankFusion'" of a server before MongoDB 8.1
    static final int UNRECOGNIZED_STAGE = 40324;
    // QueryFeatureNotAllowed: the stage is known but disabled, such as below feature compatibility version 8.1
    static final int QUERY_FEATURE_NOT_ALLOWED = 224;

    private static final Logger LOG = LoggerFactory.getLogger(RankFusionAvailability.class);
    private static final String STAGE = "$rankFusion";

    private final AtomicBoolean unavailable = new AtomicBoolean();

    /**
     * @return Whether the server runs {@code $rankFusion}, as far as known
     */
    boolean isAvailable() {
        return !unavailable.get();
    }

    /**
     * Records the failure of a {@code $rankFusion} aggregation.
     *
     * @param failure The failure
     * @return Whether the server lacks the stage, so the search is fused in memory instead
     */
    boolean onFailure(Throwable failure) {
        if (!isUnknownRankFusion(failure)) {
            return false;
        }
        if (unavailable.compareAndSet(false, true) && LOG.isWarnEnabled()) {
            LOG.warn("The MongoDB server rejected the {} stage ({}); hybrid searches are fused in memory from now on. "
                + "Native fusion needs MongoDB 8.1 or later at feature compatibility version 8.1 or later.",
                STAGE, ((MongoCommandException) failure).getErrorMessage());
        }
        return true;
    }

    /**
     * @param failure A failure of a {@code $rankFusion} aggregation
     * @return Whether the server does not know or does not allow the stage
     */
    static boolean isUnknownRankFusion(Throwable failure) {
        if (!(failure instanceof MongoCommandException commandException)) {
            return false;
        }
        int code = commandException.getErrorCode();
        return (code == UNRECOGNIZED_STAGE || code == QUERY_FEATURE_NOT_ALLOWED)
            && String.valueOf(commandException.getErrorMessage()).contains(STAGE);
    }
}

/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.mongodb.operations.options;

import com.mongodb.client.model.Collation;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Nullable;
import org.bson.conversions.Bson;

/**
 * The MongoDB's aggregation options.
 *
 * @author Denis Stepanov
 * @since 3.3.0
 */
@Experimental
public final class MongoAggregationOptions {

    private Boolean allowDiskUse;
    private Long maxTimeMS;
    private Long maxAwaitTimeMS;
    private Boolean bypassDocumentValidation;
    private Collation collation;
    private String comment;
    private Bson hint;

    public MongoAggregationOptions() {
    }

    public MongoAggregationOptions(MongoAggregationOptions options) {
        allowDiskUse = options.allowDiskUse;
        maxTimeMS = options.maxTimeMS;
        maxAwaitTimeMS = options.maxAwaitTimeMS;
        bypassDocumentValidation = options.bypassDocumentValidation;
        collation = options.collation;
        comment = options.comment;
        hint = options.hint;
    }

    public void copyNotNullFrom(MongoAggregationOptions options) {
        if (options.allowDiskUse != null) {
            allowDiskUse = options.allowDiskUse;
        }
        if (options.maxTimeMS != null) {
            maxTimeMS = options.maxTimeMS;
        }
        if (options.maxAwaitTimeMS != null) {
            maxAwaitTimeMS = options.maxAwaitTimeMS;
        }
        if (options.bypassDocumentValidation != null) {
            bypassDocumentValidation = options.bypassDocumentValidation;
        }
        if (options.collation != null) {
            collation = options.collation;
        }
        if (options.comment != null) {
            comment = options.comment;
        }
        if (options.hint != null) {
            hint = options.hint;
        }
    }

    public boolean isEmpty() {
        if (allowDiskUse != null) {
            return false;
        }
        if (maxTimeMS != null) {
            return false;
        }
        if (maxAwaitTimeMS != null) {
            return false;
        }
        if (bypassDocumentValidation != null) {
            return false;
        }
        if (collation != null) {
            return false;
        }
        if (comment != null) {
            return false;
        }
        return hint == null;
    }

    @Nullable
    public Boolean getAllowDiskUse() {
        return allowDiskUse;
    }

    public MongoAggregationOptions allowDiskUse(Boolean allowDiskUse) {
        this.allowDiskUse = allowDiskUse;
        return this;
    }

    @Nullable
    public Long getMaxTimeMS() {
        return maxTimeMS;
    }

    public MongoAggregationOptions maxTimeMS(Long maxTimeMS) {
        this.maxTimeMS = maxTimeMS;
        return this;
    }

    @Nullable
    public Long getMaxAwaitTimeMS() {
        return maxAwaitTimeMS;
    }

    public MongoAggregationOptions maxAwaitTimeMS(Long maxAwaitTimeMS) {
        this.maxAwaitTimeMS = maxAwaitTimeMS;
        return this;
    }

    @Nullable
    public Boolean getBypassDocumentValidation() {
        return bypassDocumentValidation;
    }

    public MongoAggregationOptions bypassDocumentValidation(Boolean bypassDocumentValidation) {
        this.bypassDocumentValidation = bypassDocumentValidation;
        return this;
    }

    @Nullable
    public Collation getCollation() {
        return collation;
    }

    public MongoAggregationOptions collation(Collation collation) {
        this.collation = collation;
        return this;
    }

    @Nullable
    public String getComment() {
        return comment;
    }

    public MongoAggregationOptions comment(String comment) {
        this.comment = comment;
        return this;
    }

    @Nullable
    public Bson getHint() {
        return hint;
    }

    public MongoAggregationOptions hint(Bson hint) {
        this.hint = hint;
        return this;
    }
}

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

import com.mongodb.CursorType;
import com.mongodb.client.model.Collation;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Nullable;
import org.bson.conversions.Bson;

/**
 * The MongoDB's find options.
 *
 * @author Denis Stepanov
 * @since 3.3.0
 */
@Experimental
public final class MongoFindOptions {
    private Bson filter;
    private Integer batchSize;
    private Integer limit;
    private Bson projection;
    private Long maxTimeMS;
    private Long maxAwaitTimeMS;
    private Integer skip;
    private Bson sort;
    private CursorType cursorType;
    private Boolean noCursorTimeout;
    private Boolean partial;
    private Collation collation;
    private String comment;
    private Bson hint;
    private Bson max;
    private Bson min;
    private Boolean returnKey;
    private Boolean showRecordId;
    private Boolean allowDiskUse;

    public MongoFindOptions() {
    }

    public MongoFindOptions(MongoFindOptions options) {
        filter = options.filter;
        batchSize = options.batchSize;
        limit = options.limit;
        projection = options.projection;
        maxTimeMS = options.maxTimeMS;
        maxAwaitTimeMS = options.maxAwaitTimeMS;
        skip = options.skip;
        sort = options.sort;
        cursorType = options.cursorType;
        noCursorTimeout = options.noCursorTimeout;
        partial = options.partial;
        collation = options.collation;
        comment = options.comment;
        hint = options.hint;
        max = options.max;
        min = options.min;
        returnKey = options.returnKey;
        showRecordId = options.showRecordId;
        allowDiskUse = options.allowDiskUse;
    }

    @Nullable
    public Bson getFilter() {
        return filter;
    }

    public MongoFindOptions filter(Bson filter) {
        this.filter = filter;
        return this;
    }

    @Nullable
    public Integer getBatchSize() {
        return batchSize;
    }

    public MongoFindOptions batchSize(Integer batchSize) {
        this.batchSize = batchSize;
        return this;
    }

    @Nullable
    public Integer getLimit() {
        return limit;
    }

    public MongoFindOptions limit(Integer limit) {
        this.limit = limit;
        return this;
    }

    @Nullable
    public Bson getProjection() {
        return projection;
    }

    public MongoFindOptions projection(Bson projection) {
        this.projection = projection;
        return this;
    }

    @Nullable
    public Long getMaxTimeMS() {
        return maxTimeMS;
    }

    public MongoFindOptions maxTimeMS(Long maxTimeMS) {
        this.maxTimeMS = maxTimeMS;
        return this;
    }

    @Nullable
    public Long getMaxAwaitTimeMS() {
        return maxAwaitTimeMS;
    }

    public MongoFindOptions maxAwaitTimeMS(Long maxAwaitTimeMS) {
        this.maxAwaitTimeMS = maxAwaitTimeMS;
        return this;
    }

    @Nullable
    public Integer getSkip() {
        return skip;
    }

    public MongoFindOptions skip(Integer skip) {
        this.skip = skip;
        return this;
    }

    @Nullable
    public Bson getSort() {
        return sort;
    }

    public MongoFindOptions sort(Bson sort) {
        this.sort = sort;
        return this;
    }

    @Nullable
    public CursorType getCursorType() {
        return cursorType;
    }

    public MongoFindOptions cursorType(CursorType cursorType) {
        this.cursorType = cursorType;
        return this;
    }

    @Nullable
    public Boolean getNoCursorTimeout() {
        return noCursorTimeout;
    }

    public MongoFindOptions noCursorTimeout(Boolean noCursorTimeout) {
        this.noCursorTimeout = noCursorTimeout;
        return this;
    }

    @Nullable
    public Boolean getPartial() {
        return partial;
    }

    public MongoFindOptions partial(Boolean partial) {
        this.partial = partial;
        return this;
    }

    @Nullable
    public Collation getCollation() {
        return collation;
    }

    public MongoFindOptions collation(Collation collation) {
        this.collation = collation;
        return this;
    }

    @Nullable
    public String getComment() {
        return comment;
    }

    public MongoFindOptions comment(String comment) {
        this.comment = comment;
        return this;
    }

    @Nullable
    public Bson getHint() {
        return hint;
    }

    public MongoFindOptions hint(Bson hint) {
        this.hint = hint;
        return this;
    }

    @Nullable
    public Bson getMax() {
        return max;
    }

    public MongoFindOptions max(Bson max) {
        this.max = max;
        return this;
    }

    @Nullable
    public Bson getMin() {
        return min;
    }

    public MongoFindOptions min(Bson min) {
        this.min = min;
        return this;
    }

    @Nullable
    public Boolean getReturnKey() {
        return returnKey;
    }

    public MongoFindOptions returnKey(Boolean returnKey) {
        this.returnKey = returnKey;
        return this;
    }

    @Nullable
    public Boolean getShowRecordId() {
        return showRecordId;
    }

    public MongoFindOptions showRecordId(Boolean showRecordId) {
        this.showRecordId = showRecordId;
        return this;
    }

    @Nullable
    public Boolean getAllowDiskUse() {
        return allowDiskUse;
    }

    public MongoFindOptions allowDiskUse(Boolean allowDiskUse) {
        this.allowDiskUse = allowDiskUse;
        return this;
    }
}

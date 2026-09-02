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
package io.micronaut.data.mongodb.conf;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.ReflectiveAccess;
import org.jspecify.annotations.NullUnmarked;

/**
 * Micronaut Data MongoDB integration configuration.
 */
@ConfigurationProperties(MongoDataConfiguration.PREFIX)
@ReflectiveAccess
@NullUnmarked
public final class MongoDataConfiguration {

    public static final String PREFIX = "micronaut.data.mongodb";
    public static final String CREATE_COLLECTIONS_PROPERTY = PREFIX + ".create-collections";
    public static final String CREATE_INDEXES_PROPERTY = PREFIX + ".create-indexes";
    public static final String CREATE_INDEXES_FAILURE_POLICY_PROPERTY = PREFIX + ".create-indexes-failure-policy";
    public static final String DRIVER_TYPE_PROPERTY = PREFIX + ".driver-type";
    public static final String JSON_VIEWS_PROPERTY = PREFIX + ".ignore-json-views";
    public static final String DRIVER_TYPE_SYNC = DriverType.SYNC.name();
    public static final String DRIVER_TYPE_REACTIVE = DriverType.REACTIVE.name();
    public static final String DATABASE_CONFIGURATION_ERROR_MESSAGE = "MongoDB database name is not specified in the url! You can specify it as '@MongoRepository(databaseName: \"mydb\")' or in the connect url: 'mongodb://username:password@localhost:27017/mydb'.";
    /**
     * Create MongoDB collection at app initialization.
     */
    private boolean createCollections;

    /**
     * Create MongoDB indexes at app initialization.
     */
    private boolean createIndexes;

    /**
     * Policy controlling application startup behavior when MongoDB index initialization fails.
     */
    private IndexCreationFailurePolicy createIndexesFailurePolicy = IndexCreationFailurePolicy.FAIL_FAST;

    /**
     * Choose the appropriate driver type when both are on classpath.
     */
    private DriverType driverType;

    /**
     * Ignore any JsonView annotations on the properties of mapped entity during encode and decode operations.
     */
    private boolean ignoreJsonViews;

    public boolean isCreateCollections() {
        return createCollections;
    }

    public void setCreateCollections(boolean createCollections) {
        this.createCollections = createCollections;
    }

    public boolean isCreateIndexes() {
        return createIndexes;
    }

    public void setCreateIndexes(boolean createIndexes) {
        this.createIndexes = createIndexes;
    }

    public boolean isIgnoreJsonViews() {
        return ignoreJsonViews;
    }

    public void setIgnoreJsonViews(boolean ignoreJsonViews) {
        this.ignoreJsonViews = ignoreJsonViews;
    }

    public IndexCreationFailurePolicy getCreateIndexesFailurePolicy() {
        return createIndexesFailurePolicy;
    }

    public void setCreateIndexesFailurePolicy(IndexCreationFailurePolicy createIndexesFailurePolicy) {
        this.createIndexesFailurePolicy = createIndexesFailurePolicy;
    }

    public DriverType getDriverType() {
        return driverType;
    }

    public void setDriverType(DriverType driverType) {
        this.driverType = driverType;
    }

    /**
     * The driver type.
     */
    public enum DriverType {
        SYNC, REACTIVE
    }

    /**
     * Startup behavior policy when index creation fails.
     */
    public enum IndexCreationFailurePolicy {
        /**
         * Fail startup immediately when index initialization fails.
         */
        FAIL_FAST,

        /**
         * Log and continue startup when index initialization fails.
         */
        WARN_AND_CONTINUE
    }

    /**
     * Not reactive driver condition.
     */
    public static final class NotReactiveDriverSelectedCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context) {
            return context.getBean(MongoDataConfiguration.class).driverType != DriverType.REACTIVE;
        }
    }

    /**
     * Not sync driver condition.
     */
    public static final class NotSyncDriverSelectedCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context) {
            return context.getBean(MongoDataConfiguration.class).driverType != DriverType.SYNC;
        }
    }

    /**
     * Collection initialization enabled condition.
     */
    @Internal
    public static final class CollectionInitializationEnabledCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context) {
            MongoDataConfiguration configuration = context.getBean(MongoDataConfiguration.class);
            return configuration.isCreateCollections() || configuration.isCreateIndexes();
        }
    }

}

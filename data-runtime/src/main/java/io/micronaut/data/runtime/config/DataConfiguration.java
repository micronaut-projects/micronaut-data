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
package io.micronaut.data.runtime.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.StringUtils;
import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * General data configuration.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@ConfigurationProperties(DataSettings.PREFIX)
public class DataConfiguration implements DataSettings {

    /**
     * The configuration property that makes repository save methods use insert operations only.
     */
    public static final String SAVE_AS_INSERT_PROPERTY = DataSettings.PREFIX + ".save-as-insert";

    /**
     * The configuration property that makes repository save methods fall back to update when insert detects
     * that an entity with an assigned identity already exists.
     */
    public static final String SAVE_ASSIGNED_ID_FALLBACK_TO_UPDATE_PROPERTY = DataSettings.PREFIX + ".save-assigned-id-fallback-to-update";

    private boolean saveAsInsert;
    private boolean saveAssignedIdFallbackToUpdate;

    /**
     * @return Whether repository save methods should always use insert operations.
     */
    public boolean isSaveAsInsert() {
        return saveAsInsert;
    }

    /**
     * @return Whether repository save methods should fall back to update for entities with assigned identities.
     */
    public boolean isSaveAssignedIdFallbackToUpdate() {
        return saveAssignedIdFallbackToUpdate;
    }

    /**
     * Sets whether repository save methods should always use insert operations.
     * This restores the Micronaut Data 4 behavior where {@code save} and {@code saveAll}
     * always inserted entities instead of selecting insert or update based on identity state.
     *
     * @param saveAsInsert Whether repository save methods should always use insert operations
     */
    public void setSaveAsInsert(boolean saveAsInsert) {
        this.saveAsInsert = saveAsInsert;
    }

    /**
     * Sets whether repository save methods should fall back to update when an insert detects that an entity
     * with a non-generated identity already exists.
     *
     * @param saveAssignedIdFallbackToUpdate Whether repository save methods should fall back to update
     */
    public void setSaveAssignedIdFallbackToUpdate(boolean saveAssignedIdFallbackToUpdate) {
        this.saveAssignedIdFallbackToUpdate = saveAssignedIdFallbackToUpdate;
    }

    /**
     * Configuration for pageable.
     */
    @ConfigurationProperties(PageableConfiguration.PREFIX)
    public static class PageableConfiguration {
        public static final int    DEFAULT_MAX_PAGE_SIZE = 100;
        public static final boolean DEFAULT_SORT_IGNORE_CASE = false;
        public static final String DEFAULT_SORT_PARAMETER = "sort";
        public static final String DEFAULT_SIZE_PARAMETER = "size";
        public static final String DEFAULT_PAGE_PARAMETER = "page";
        public static final String PREFIX = "pageable";
        private int maxPageSize = DEFAULT_MAX_PAGE_SIZE;
        @Nullable
        private Integer defaultPageSize = null; // When is not specified the maxPageSize should be used
        private boolean sortIgnoreCase = DEFAULT_SORT_IGNORE_CASE;
        private String sortParameterName = DEFAULT_SORT_PARAMETER;
        private String sizeParameterName = DEFAULT_SIZE_PARAMETER;
        private String pageParameterName = DEFAULT_PAGE_PARAMETER;
        private Pattern sortDelimiter = Pattern.compile(",");

        /**
         * @return Whether sort ignores case.
         */
        public boolean isSortIgnoreCase() {
            return sortIgnoreCase;
        }

        /**
         * @param sortIgnoreCase Whether sort ignores case
         */
        public void setSortIgnoreCase(boolean sortIgnoreCase) {
            this.sortIgnoreCase = sortIgnoreCase;
        }

        /**
         * @return The delimiter to use to calculate sort order. Defaults to {@code ,}.
         */
        public Pattern getSortDelimiterPattern() {
            return sortDelimiter;
        }

        /**
         * @param sortDelimiter The delimiter to use to calculate sort order. Defaults to {@code ,}.
         */
        public void setSortDelimiter(String sortDelimiter) {
            if (StringUtils.isNotEmpty(sortDelimiter)) {
                this.sortDelimiter = Pattern.compile(Pattern.quote(sortDelimiter));
            }
        }

        /**
         * @return The maximum page size when binding {@link io.micronaut.data.model.Pageable} objects.
         */
        public int getMaxPageSize() {
            return maxPageSize;
        }

        /**
         * Sets the maximum page size when binding {@link io.micronaut.data.model.Pageable} objects.
         * @param maxPageSize The max page size
         */
        public void setMaxPageSize(int maxPageSize) {
            this.maxPageSize = maxPageSize;
        }

        /**
         * @return the page size to use when binding {@link io.micronaut.data.model.Pageable}
         * objects and no size parameter is used. By default, is set to the same vale as {@link #maxPageSize}
         */
        public int getDefaultPageSize() {
            return defaultPageSize == null ? maxPageSize : defaultPageSize;
        }

        /**
         * Sets the default page size when binding {@link io.micronaut.data.model.Pageable} objects and no size
         * parameter is used. Should be smaller or equal than {@link #maxPageSize}.
         *
         * @param defaultPageSize The default page size
         */
        public void setDefaultPageSize(int defaultPageSize) {
            this.defaultPageSize = defaultPageSize;
        }

        /**
         * @return The default sort parameter name
         */
        public String getSortParameterName() {
            return sortParameterName;
        }

        /**
         * @param sortParameterName The default sort parameter name
         */
        public void setSortParameterName(String sortParameterName) {
            if (StringUtils.isNotEmpty(sortParameterName)) {
                this.sortParameterName = sortParameterName;
            }
        }

        /**
         * @return The default size parameter name
         */
        public String getSizeParameterName() {
            return sizeParameterName;
        }

        /**
         * @param sizeParameterName The default size parameter name
         */
        public void setSizeParameterName(String sizeParameterName) {
            if (StringUtils.isNotEmpty(sizeParameterName)) {
                this.sizeParameterName = sizeParameterName;
            }
        }

        /**
         * @return The default page parameter name
         */
        public String getPageParameterName() {
            return pageParameterName;
        }

        /**
         * @param pageParameterName Sets the default page parameter name
         */
        public void setPageParameterName(String pageParameterName) {
            if (StringUtils.isNotEmpty(sizeParameterName)) {
                this.pageParameterName = pageParameterName;
            }
        }
    }
}

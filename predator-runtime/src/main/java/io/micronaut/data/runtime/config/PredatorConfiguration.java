package io.micronaut.data.runtime.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.StringUtils;

import javax.validation.constraints.Min;

/**
 * General predator configuration.
 *
 * @author graemerocher
 * @since 1.0.0
 */
@ConfigurationProperties(PredatorSettings.PREFIX)
public class PredatorConfiguration implements PredatorSettings {


    /**
     * Configuration for pageable.
     */
    @ConfigurationProperties(PageableConfiguration.PREFIX)
    public static class PageableConfiguration {
        public static final String DEFAULT_SORT_PARAMETER = "sort";
        public static final String DEFAULT_SIZE_PARAMETER = "size";
        public static final String DEFAULT_PAGE_PARAMETER = "page";
        public static final String PREFIX = "pageable";
        private int maxPageSize = 100;
        private String sortParameterName = DEFAULT_SORT_PARAMETER;
        private String sizeParameterName = DEFAULT_SIZE_PARAMETER;
        private String pageParameterName = DEFAULT_PAGE_PARAMETER;

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

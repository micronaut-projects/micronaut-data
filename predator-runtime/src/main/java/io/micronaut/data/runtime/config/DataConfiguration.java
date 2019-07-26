package io.micronaut.data.runtime.config;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.util.StringUtils;
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
     * Configuration for pageable.
     */
    @ConfigurationProperties(PageableConfiguration.PREFIX)
    public static class PageableConfiguration {
        public static final String DEFAULT_SORT_PARAMETER = "sort";
        public static final String DEFAULT_SIZE_PARAMETER = "size";
        public static final String DEFAULT_PAGE_PARAMETER = "page";
        public static final String PREFIX = "pageable";
        private int maxPageSize = 100;
        private boolean sortIgnoreCase = false;
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

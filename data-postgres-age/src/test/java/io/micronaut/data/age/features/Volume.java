package io.micronaut.data.age.features;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.Indexes;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.graph.annotation.Node;

/**
 * A versioned node with an assigned identifier and no repository of its own: it is written through a shelf. Its
 * composite indexes have generated names that only differ past the 63 bytes of a PostgreSQL identifier.
 */
@Node
@Indexes({
    @Index(columns = {"firstAuthorFamilyName", "firstAuthorGivenName", "publisherCountryCode"}),
    @Index(columns = {"firstAuthorFamilyName", "firstAuthorGivenName", "publisherRegionCode"})
})
public class Volume {
    @Id
    private final String code;
    @Index(columns = "isbn", unique = true)
    private final String isbn;
    private String firstAuthorFamilyName;
    private String firstAuthorGivenName;
    private String publisherCountryCode;
    private String publisherRegionCode;
    @Version
    @Index(columns = "version")
    private Long version;

    public Volume(String code, String isbn) {
        this.code = code;
        this.isbn = isbn;
    }

    public String getCode() {
        return code;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getFirstAuthorFamilyName() {
        return firstAuthorFamilyName;
    }

    public void setFirstAuthorFamilyName(String firstAuthorFamilyName) {
        this.firstAuthorFamilyName = firstAuthorFamilyName;
    }

    public String getFirstAuthorGivenName() {
        return firstAuthorGivenName;
    }

    public void setFirstAuthorGivenName(String firstAuthorGivenName) {
        this.firstAuthorGivenName = firstAuthorGivenName;
    }

    public String getPublisherCountryCode() {
        return publisherCountryCode;
    }

    public void setPublisherCountryCode(String publisherCountryCode) {
        this.publisherCountryCode = publisherCountryCode;
    }

    public String getPublisherRegionCode() {
        return publisherRegionCode;
    }

    public void setPublisherRegionCode(String publisherRegionCode) {
        this.publisherRegionCode = publisherRegionCode;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}

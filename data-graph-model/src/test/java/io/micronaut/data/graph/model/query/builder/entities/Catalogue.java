package io.micronaut.data.graph.model.query.builder.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.Indexes;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.graph.annotation.Node;

@Node
@Indexes({
    @Index(columns = {"firstAuthorFamilyName", "firstAuthorGivenName", "publisherCountryCode"}),
    @Index(columns = {"firstAuthorFamilyName", "firstAuthorGivenName", "publisherRegionCode"}),
    @Index(columns = "publisherRegionCode", name = "a_name_that_is_declared")
})
public class Catalogue {
    @Id
    @GeneratedValue
    private Long id;
    private String firstAuthorFamilyName;
    private String firstAuthorGivenName;
    private String publisherCountryCode;
    private String publisherRegionCode;
    @Version
    @Index(columns = "version")
    private Long version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

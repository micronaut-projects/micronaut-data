package io.micronaut.data.tck.entities;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.Index;

@Embeddable
public class Jurisdiction {

    private String countryCode;

    @Index(columns = "region_code")
    private String regionCode;

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }
}

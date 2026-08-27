package io.micronaut.data.runtime.mapper.sql;

import io.micronaut.core.annotation.Creator;
import io.micronaut.data.annotation.Embeddable;

@Embeddable
class CountryWithoutNoArg {

    private String countryCode;
    private String regionCode;

    CountryWithoutNoArg(String countryCode, String regionCode) {
        this.countryCode = countryCode;
        this.regionCode = regionCode;
    }

    @Creator
    static CountryWithoutNoArg fromJson(String value) {
        return new CountryWithoutNoArg(value.substring(0, 2),
            value.length() > 3 ? value.substring(3) : null);
    }

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

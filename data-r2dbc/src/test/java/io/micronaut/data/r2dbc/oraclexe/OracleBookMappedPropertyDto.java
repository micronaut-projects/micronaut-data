package io.micronaut.data.r2dbc.oraclexe;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.MappedProperty;

@Introspected
public class OracleBookMappedPropertyDto {

    @MappedProperty("TITLE")
    private String renamedTitle;

    @MappedProperty("TOTAL_PAGES")
    private int renamedPages;

    public String getRenamedTitle() {
        return renamedTitle;
    }

    public void setRenamedTitle(String renamedTitle) {
        this.renamedTitle = renamedTitle;
    }

    public int getRenamedPages() {
        return renamedPages;
    }

    public void setRenamedPages(int renamedPages) {
        this.renamedPages = renamedPages;
    }
}

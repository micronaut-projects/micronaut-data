package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;

@Embeddable
@JsonView(table_name = "TBL_ADDRESS")
public class AddressView {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}

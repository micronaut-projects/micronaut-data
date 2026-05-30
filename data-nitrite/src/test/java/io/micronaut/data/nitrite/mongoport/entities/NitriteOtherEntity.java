package io.micronaut.data.nitrite.mongoport.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.math.BigDecimal;

@MappedEntity("nitrite_other")
public class NitriteOtherEntity {
    @Id
    @GeneratedValue
    private String id;
    private String name;
    private boolean enabled;
    private Boolean enabled2;
    private Long age;
    private BigDecimal amount;
    private BigDecimal budget;

    @Relation(value = Relation.Kind.MANY_TO_ONE)
    private NitriteTestEntity test;

    @Relation(value = Relation.Kind.MANY_TO_ONE)
    private NitriteSimpleEntity simple;

    public NitriteOtherEntity() {
    }

    public NitriteOtherEntity(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Boolean getEnabled2() {
        return enabled2;
    }

    public void setEnabled2(Boolean enabled2) {
        this.enabled2 = enabled2;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getAge() {
        return age;
    }

    public void setAge(Long age) {
        this.age = age;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public void setBudget(BigDecimal budget) {
        this.budget = budget;
    }

    public NitriteTestEntity getTest() {
        return test;
    }

    public void setTest(NitriteTestEntity test) {
        this.test = test;
    }

    public NitriteSimpleEntity getSimple() {
        return simple;
    }

    public void setSimple(NitriteSimpleEntity simple) {
        this.simple = simple;
    }
}

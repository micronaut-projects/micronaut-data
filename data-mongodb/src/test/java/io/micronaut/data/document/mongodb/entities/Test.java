package io.micronaut.data.document.mongodb.entities;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.math.BigDecimal;
import java.util.List;

@MappedEntity
class Test {
    @Id
    @GeneratedValue
    private String id;
    private String name;
    private boolean enabled;
    private Boolean enabled2;
    private Long age;
    private BigDecimal amount;
    private BigDecimal budget;

    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "test")
    private List<OtherEntity> others;

    @Relation(value = Relation.Kind.ONE_TO_ONE)
    private OtherEntity oneOther;

    @Relation(value = Relation.Kind.MANY_TO_ONE)
    private OtherEntity manyToOneOther;

    private List<String> colors;

    public Test(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
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

    public List<OtherEntity> getOthers() {
        return others;
    }

    public void setOthers(List<OtherEntity> others) {
        this.others = others;
    }

    public OtherEntity getOneOther() {
        return oneOther;
    }

    public void setOneOther(OtherEntity oneOther) {
        this.oneOther = oneOther;
    }

    public OtherEntity getManyToOneOther() {
        return manyToOneOther;
    }

    public void setManyToOneOther(OtherEntity manyToOneOther) {
        this.manyToOneOther = manyToOneOther;
    }

    public List<String> getColors() {
        return colors;
    }

    public void setColors(List<String> colors) {
        this.colors = colors;
    }
}

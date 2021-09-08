package io.micronaut.data.model.jpa.criteria


import io.micronaut.data.processor.model.SourcePersistentEntity
import io.micronaut.data.processor.model.criteria.impl.SourcePersistentEntityCriteriaBuilderImpl
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaDelete
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaQuery
import io.micronaut.data.processor.model.criteria.SourcePersistentEntityCriteriaUpdate
import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.data.tck.tests.AbstractCriteriaSpec
import io.micronaut.inject.ast.ClassElement
import jakarta.persistence.criteria.CriteriaDelete
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.CriteriaUpdate

import java.util.function.Function

class CriteriaSpec extends AbstractCriteriaSpec {

    PersistentEntityCriteriaBuilder criteriaBuilder

    SourcePersistentEntityCriteriaQuery criteriaQuery

    SourcePersistentEntityCriteriaDelete criteriaDelete

    SourcePersistentEntityCriteriaUpdate criteriaUpdate

    ClassElement testEntityElement

    Function<ClassElement, SourcePersistentEntity> entityResolver = new Function<ClassElement, SourcePersistentEntity>() {

        private Map<String, SourcePersistentEntity> entityMap = new HashMap<>()

        @Override
        SourcePersistentEntity apply(ClassElement classElement) {
            return entityMap.computeIfAbsent(classElement.getName(), { it ->
                new SourcePersistentEntity(classElement, this)
            })
        }
    }

    void setup() {
        testEntityElement = buildCustomElement()
        criteriaBuilder = new SourcePersistentEntityCriteriaBuilderImpl(entityResolver)
        criteriaQuery = criteriaBuilder.createQuery()
        criteriaDelete = criteriaBuilder.createCriteriaDelete(null)
        criteriaUpdate = criteriaBuilder.createCriteriaUpdate(null)
    }

    @Override
    PersistentEntityRoot createRoot(CriteriaQuery query) {
        return query.from(testEntityElement)
    }

    @Override
    PersistentEntityRoot createRoot(CriteriaDelete query) {
        return query.from(testEntityElement)
    }

    @Override
    PersistentEntityRoot createRoot(CriteriaUpdate query) {
        return query.from(testEntityElement)
    }

    private ClassElement buildCustomElement() {
        new CustomAbstractDataSpec().buildClassElement("""
package test;
import io.micronaut.data.annotation.*;
import java.math.*;
import java.util.List;

@MappedEntity
class Test {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;
    private String name;
    private boolean enabled;
    private Boolean enabled2;
    private Long age;
    private BigDecimal amount;
    private BigDecimal budget;
    
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "test")
    private List<OtherEntity> others;

    public Test(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
    
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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
}

@MappedEntity
class OtherEntity {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;
    private String name;
    private boolean enabled;
    private Boolean enabled2;
    private Long age;
    private BigDecimal amount;
    private BigDecimal budget;
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    private Test test;
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    private SimpleEntity simple;
    
    public OtherEntity(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
    
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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
    
    public Test getTest() {
        return test;
    }

    public void setTest(Test test) {
        this.test = test;
    }
    
    public SimpleEntity getSimple() {
        return simple;
    }

    public void setSimple(SimpleEntity simple) {
        this.simple = simple;
    }

}

@MappedEntity
class SimpleEntity {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;
    private String name;
    private boolean enabled;
    private Boolean enabled2;
    private Long age;
    private BigDecimal amount;
    private BigDecimal budget;
    
    public SimpleEntity(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
    
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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

}

""")
    }

    static class CustomAbstractDataSpec extends AbstractDataSpec {

        ClassElement buildClassElement(String s) {
            return super.buildClassElement(s)
        }
    }

}

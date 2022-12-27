package io.micronaut.data.runtime.criteria;

import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;

import jakarta.annotation.Generated;
import java.math.BigDecimal;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(SimpleEntity.class)
public abstract class SimpleEntity_ {

	public static volatile SingularAttribute<SimpleEntity, Boolean> enabled2;
	public static volatile SingularAttribute<SimpleEntity, BigDecimal> amount;
	public static volatile SingularAttribute<SimpleEntity, String> name;
	public static volatile SingularAttribute<SimpleEntity, Long> id;
	public static volatile SingularAttribute<SimpleEntity, Boolean> enabled;
	public static volatile SingularAttribute<SimpleEntity, Long> age;
	public static volatile SingularAttribute<SimpleEntity, BigDecimal> budget;

	public static final String ENABLED2 = "enabled2";
	public static final String AMOUNT = "amount";
	public static final String NAME = "name";
	public static final String ID = "id";
	public static final String ENABLED = "enabled";
	public static final String AGE = "age";
	public static final String BUDGET = "budget";

}


package io.micronaut.data.runtime.criteria;

import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;

import jakarta.annotation.Generated;
import java.math.BigDecimal;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(io.micronaut.data.runtime.criteria.OtherEntity.class)
public abstract class OtherEntity_ {

	public static volatile SingularAttribute<OtherEntity, Boolean> enabled2;
	public static volatile SingularAttribute<OtherEntity, BigDecimal> amount;
	public static volatile SingularAttribute<OtherEntity, Test> test;
	public static volatile SingularAttribute<OtherEntity, String> name;
	public static volatile SingularAttribute<OtherEntity, SimpleEntity> simple;
	public static volatile SingularAttribute<OtherEntity, Long> id;
	public static volatile SingularAttribute<OtherEntity, Boolean> enabled;
	public static volatile SingularAttribute<OtherEntity, Long> age;
	public static volatile SingularAttribute<OtherEntity, BigDecimal> budget;

	public static final String ENABLED2 = "enabled2";
	public static final String AMOUNT = "amount";
	public static final String TEST = "test";
	public static final String NAME = "name";
	public static final String SIMPLE = "simple";
	public static final String ID = "id";
	public static final String ENABLED = "enabled";
	public static final String AGE = "age";
	public static final String BUDGET = "budget";

}


package io.micronaut.data.runtime.criteria;

import jakarta.persistence.metamodel.ListAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.persistence.metamodel.StaticMetamodel;

import jakarta.annotation.Generated;
import java.math.BigDecimal;

@Generated(value = "org.hibernate.jpamodelgen.JPAMetaModelEntityProcessor")
@StaticMetamodel(Test.class)
public abstract class Test_ {

	public static volatile SingularAttribute<Test, Boolean> enabled2;
	public static volatile SingularAttribute<Test, BigDecimal> amount;
	public static volatile SingularAttribute<Test, String> name;
	public static volatile SingularAttribute<Test, Long> id;
	public static volatile SingularAttribute<Test, Boolean> enabled;
	public static volatile SingularAttribute<Test, Long> age;
	public static volatile ListAttribute<Test, OtherEntity> others;
	public static volatile SingularAttribute<Test, BigDecimal> budget;

	public static final String ENABLED2 = "enabled2";
	public static final String AMOUNT = "amount";
	public static final String NAME = "name";
	public static final String ID = "id";
	public static final String ENABLED = "enabled";
	public static final String AGE = "age";
	public static final String OTHERS = "others";
	public static final String BUDGET = "budget";

}


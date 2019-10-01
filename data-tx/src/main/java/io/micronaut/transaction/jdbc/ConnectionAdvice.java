package io.micronaut.transaction.jdbc;

import io.micronaut.aop.Introduction;
import io.micronaut.context.annotation.Type;

import javax.inject.Scope;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Scope
@Introduction
@Type(ConnectionInterceptor.class)
public @interface ConnectionAdvice {
}

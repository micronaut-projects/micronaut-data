/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.runtime.criteria

import io.micronaut.core.annotation.Experimental
import io.micronaut.data.repository.jpa.criteria.CriteriaDeleteBuilder
import io.micronaut.data.repository.jpa.criteria.CriteriaQueryBuilder
import io.micronaut.data.repository.jpa.criteria.CriteriaUpdateBuilder
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import jakarta.persistence.criteria.*
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

@Experimental
fun <E, Y> KProperty<Y?>.asPath(root: Root<E>): Path<Y> = root.get(name)

@Experimental
operator fun <X, V> Path<X>.get(prop: KProperty1<out X, V>): Path<V> {
    return get(prop.name)
}

@Experimental
fun <E, I, K : Collection<I>?> From<*, E>.joinMany(prop: KProperty1<out E, K>, joinType: JoinType? = null): Join<E, I> {
    return if (joinType == null) {
        this.join(prop.name)
    } else {
        this.join(prop.name, joinType)
    }
}

@Experimental
fun <E, I> From<*, E>.joinOne(prop: KProperty1<out E, I>, joinType: JoinType? = null): Join<E, I> {
    return if (joinType == null) {
        this.join(prop.name)
    } else {
        this.join(prop.name, joinType)
    }
}

@Experimental
inline fun <reified E> where(noinline dsl: Where<E>.() -> Unit) = WherePredicate(dsl)

@Experimental
inline fun <reified E, reified R> query(noinline dsl: SelectQuery<E, R>.() -> Unit) = QueryBuilder(dsl, E::class.java, R::class.java)

@Experimental
inline fun <reified E> update(noinline dsl: UpdateQuery<E>.() -> Unit) = UpdateQueryBuilder(dsl, E::class.java)

@Experimental
class QueryBuilder<E, R>(private var dsl: SelectQuery<E, R>.() -> Unit, private var entityType: Class<E>, var resultType: Class<R>) : CriteriaQueryBuilder<R> {

    override fun build(criteriaBuilder: CriteriaBuilder): CriteriaQuery<R> {
        val criteriaQuery = criteriaBuilder.createQuery(resultType)
        val root = criteriaQuery.from(entityType)
        val selectQuery = SelectQuery<E, R>(root, criteriaQuery, criteriaBuilder)
        dsl.invoke(selectQuery)
        return criteriaQuery
    }

}

@Experimental
class UpdateQueryBuilder<E>(private var dsl: UpdateQuery<E>.() -> Unit, private var entityType: Class<E>) : CriteriaUpdateBuilder<E> {

    override fun build(criteriaBuilder: CriteriaBuilder): CriteriaUpdate<E> {
        val criteriaUpdate = criteriaBuilder.createCriteriaUpdate(entityType)
        val updateQuery = UpdateQuery<E>(criteriaUpdate.root, criteriaUpdate, criteriaBuilder)
        dsl.invoke(updateQuery)
        return criteriaUpdate
    }

}

@Experimental
class DeleteQueryBuilder<E>(private var dsl: Where<E>.() -> Unit, private var entityType: Class<E>) : CriteriaDeleteBuilder<E> {

    override fun build(criteriaBuilder: CriteriaBuilder): CriteriaDelete<E> {
        val criteriaDelete = criteriaBuilder.createCriteriaDelete(entityType)
        val whereQuery = Where<E>(criteriaDelete.root, criteriaBuilder)
        dsl.invoke(whereQuery)
        criteriaDelete.where(whereQuery.toPredicate(true))
        return criteriaDelete
    }

}

@Experimental
class WherePredicate<T>(var where: Where<T>.() -> Unit) : PredicateSpecification<T> {

    override fun toPredicate(root: Root<T>, criteriaBuilder: CriteriaBuilder): Predicate {
        val query = Where(root, criteriaBuilder)
        where.invoke(query)
        return query.toPredicate(true)
    }

}

@Experimental
class SelectQuery<T, V>(var root: Root<T>, var query: CriteriaQuery<V>, var criteriaBuilder: CriteriaBuilder) : WhereQuery<T>(root, criteriaBuilder) {


    fun select(prop: KProperty<V>) {
        select(prop.asPath(root))
    }

    fun select(expression: Expression<V>) {
        query.select(expression)
    }

    fun multiselect(vararg props: KProperty<Any>) {
        query.multiselect(props.map { it.asPath(root) })
    }

    fun multiselect(vararg props: Selection<*>) {
        query.multiselect(*props)
    }

    override fun where(dsl: Where<T>.() -> Unit) {
        super.where(dsl)
        query.where(predicate)
    }

    fun avg(prop: KProperty<Number?>): Expression<Double> {
        return criteriaBuilder.avg(prop.asPath(root))
    }

    fun <N : Number> sum(prop: KProperty<N?>): Expression<N> {
        return criteriaBuilder.sum(prop.asPath(root))
    }

    fun sumAsLong(prop: KProperty<Int?>): Expression<Long> {
        return criteriaBuilder.sumAsLong(prop.asPath(root))
    }

    fun sumAsDouble(prop: KProperty<Float?>): Expression<Double> {
        return criteriaBuilder.sumAsDouble(prop.asPath(root))
    }

    fun <K> Selection<K>.alias(prop: KProperty<K?>): Selection<K> {
        return alias(prop.name)
    }

    fun <K : Number> max(prop: KProperty<K?>): Expression<K> {
        return criteriaBuilder.max(prop.asPath(root))
    }

    fun <K : Number> min(prop: KProperty<K?>): Expression<K> {
        return criteriaBuilder.min(prop.asPath(root))
    }

    fun <K : Comparable<K>?> greatest(prop: KProperty<K?>): Expression<K> {
        return criteriaBuilder.greatest(prop.asPath(root))
    }

    fun <K : Comparable<K>?> least(prop: KProperty<K?>): Expression<K> {
        return criteriaBuilder.least(prop.asPath(root))
    }

    fun <K> count(prop: KProperty<K?>): Expression<Long> {
        return criteriaBuilder.count(prop.asPath(root))
    }

    fun <K> countDistinct(prop: KProperty<Float?>): Expression<Long> {
        return criteriaBuilder.countDistinct(prop.asPath(root))
    }

}

@Experimental
class UpdateQuery<T>(var root: Root<T>, var query: CriteriaUpdate<T>, var criteriaBuilder: CriteriaBuilder) : WhereQuery<T>(root, criteriaBuilder) {

    fun <V> set(prop: KProperty<V>, value: V) {
        query.set(prop.asPath(root), value)
    }

    override fun where(dsl: Where<T>.() -> Unit) {
        super.where(dsl)
        query.where(predicate)
    }

}

@Experimental
open class WhereQuery<T>(private var root: Root<T>, private var criteriaBuilder: CriteriaBuilder) {

    var predicate: Predicate? = null

    open fun where(dsl: Where<T>.() -> Unit) {
        val w = Where(root, criteriaBuilder)
        w.dsl()
        predicate = w.toPredicate()
    }

}

@Experimental
class Where<T>(var root: Root<T>, var criteriaBuilder: CriteriaBuilder) {

    private var predicates = mutableListOf<Predicate>()

    fun and(dsl: Where<T>.() -> Unit) {
        val w = Where(root, criteriaBuilder)
        w.dsl()
        addPredicate(w.toPredicate(true))
    }

    fun or(dsl: Where<T>.() -> Unit) {
        val w = Where(root, criteriaBuilder)
        w.dsl()
        addPredicate(w.toPredicate(false))
    }

    fun not(dsl: Where<T>.() -> Unit) {
        val w = Where(root, criteriaBuilder)
        w.dsl()
        addPredicate(w.toPredicate(true).not())
    }

    fun Expression<Boolean?>.equalsTrue() = addPredicate(criteriaBuilder.isTrue(this))

    fun Expression<Boolean?>.equalsFalse() = addPredicate(criteriaBuilder.isFalse(this))

    fun Expression<*>.equalsNull() = addPredicate(criteriaBuilder.isNull(this))

    fun Expression<*>.notEqualsNull() = addPredicate(criteriaBuilder.isNotNull(this))

    infix fun Expression<*>.equal(other: Any?) = addPredicate(criteriaBuilder::equal, other)

    infix fun Expression<*>.equal(other: Expression<*>) = addPredicateExp(criteriaBuilder::equal, other)

    infix fun Expression<*>.eq(other: Any?) = addPredicate(criteriaBuilder::equal, other)

    infix fun Expression<*>.eq(other: Expression<*>) = addPredicateExp(criteriaBuilder::equal, other)

    infix fun Expression<*>.notEqual(other: Any?) = addPredicate(criteriaBuilder::notEqual, other)

    infix fun Expression<*>.notEqual(other: Expression<*>) = addPredicateExp(criteriaBuilder::notEqual, other)

    infix fun Expression<*>.ne(other: Any?) = addPredicate(criteriaBuilder::notEqual, other)

    infix fun Expression<*>.ne(other: Expression<*>) = addPredicateExp(criteriaBuilder::notEqual, other)

    infix fun <Y : Comparable<Y>> Expression<out Y?>.greaterThan(other: Y) = addComparablePredicate(criteriaBuilder::greaterThan, other)

    infix fun <Y : Comparable<Y>> Expression<out Y?>.greaterThan(other: Expression<out Y?>) = addComparablePredicate(criteriaBuilder::greaterThan, other)

    infix fun <Y : Comparable<Y>> Expression<out Y?>.greaterThanOrEqualTo(other: Y) = addComparablePredicate(criteriaBuilder::greaterThanOrEqualTo, other)

    infix fun <Y : Comparable<Y>> Expression<out Y?>.greaterThanOrEqualTo(other: Expression<Y>) = addComparablePredicate(criteriaBuilder::greaterThanOrEqualTo, other)

    infix fun <Y : Comparable<Y>> Expression<out Y?>.lessThan(other: Y) = addComparablePredicate(criteriaBuilder::lessThan, other)

    infix fun <Y : Comparable<Y>> Expression<out Y?>.lessThan(other: Expression<out Y?>) = addComparablePredicate(criteriaBuilder::lessThan, other)

    infix fun <Y : Comparable<Y>> Expression<out Y?>.lessThanOrEqualTo(other: Y) = addComparablePredicate(criteriaBuilder::lessThan, other)

    infix fun <Y : Comparable<Y>> Expression<out Y?>.lessThanOrEqualTo(other: Expression<out Y?>) = addComparablePredicate(criteriaBuilder::lessThan, other)

    fun <Y : Comparable<Y>> Expression<out Y?>.between(x: Y, y: Y) = addPredicate(criteriaBuilder.between(this, x, y))

// Not Supported yet
//  fun <Y : Comparable<Y>> Expression<out Y?>.between(x: Expression<out Y?>, y: Expression<out Y?>) = addPredicate(criteriaBuilder.between(this, x, y))

    infix fun <Y : Number> Expression<out Y?>.gt(other: Y) = addNumberPredicate(criteriaBuilder::gt, other)

    infix fun <Y : Number> Expression<out Y?>.gt(other: Expression<out Y?>) = addNumberPredicate(criteriaBuilder::gt, other)

    infix fun <Y : Number> Expression<out Y?>.ge(other: Y) = addNumberPredicate(criteriaBuilder::ge, other)

    infix fun <Y : Number> Expression<out Y?>.ge(other: Expression<out Y?>) = addNumberPredicate(criteriaBuilder::ge, other)

    infix fun <Y : Number> Expression<out Y?>.lt(other: Y) = addNumberPredicate(criteriaBuilder::lt, other)

    infix fun <Y : Number> Expression<out Y?>.lt(other: Expression<out Y?>) = addNumberPredicate(criteriaBuilder::lt, other)

    infix fun <Y : Number> Expression<out Y?>.le(other: Y) = addNumberPredicate(criteriaBuilder::le, other)

    infix fun <Y : Number> Expression<out Y?>.le(other: Expression<out Y?>) = addNumberPredicate(criteriaBuilder::le, other)

    private inline fun <Y> Expression<out Y?>.addPredicate(fn: (Expression<out Y>, Y) -> Predicate, value: Y) {
        addPredicate(fn.invoke(this, value))
    }

    private inline fun <Y> Expression<out Y?>.addPredicateExp(fn: (Expression<out Y>, Expression<out Y>) -> Predicate, value: Expression<out Y>) {
        addPredicate(fn.invoke(this, value))
    }

    private inline fun <Y : Comparable<Y>> Expression<out Y?>.addComparablePredicate(fn: (Expression<out Y>, Y) -> Predicate, value: Y) {
        addPredicate(fn.invoke(this, value))
    }

    private inline fun <Y : Comparable<Y>> Expression<out Y?>.addComparablePredicate(fn: (Expression<out Y>, Expression<out Y>) -> Predicate, value: Expression<out Y?>) {
        addPredicate(fn.invoke(this, value))
    }

    private inline fun <Y : Number?, K : Number?> Expression<out Y?>.addNumberPredicate(fn: (Expression<out Y?>, K) -> Predicate, value: K) {
        addPredicate(fn.invoke(this, value))
    }

    private inline fun <Y : Number?, K : Number?> Expression<out Y?>.addNumberPredicate(fn: (Expression<out Y?>, Expression<out K?>) -> Predicate, value: Expression<out K?>) {
        addPredicate(fn.invoke(this, value))
    }

    private fun addPredicate(predicate: Predicate) {
        predicates.add(predicate)
    }

    fun toPredicate(isAnd: Boolean = true): Predicate = if (isAnd) {
        criteriaBuilder.and(*predicates.toTypedArray())
    } else {
        criteriaBuilder.or(*predicates.toTypedArray())
    }

}

package io.micronaut.data.nitrite.runtime.mapping;

/**
 * One {@code @JoinColumn} entry of a composite foreign key: the local document field that stores
 * the value, and the name of the property on the associated entity it mirrors.
 *
 * @param localName          the local document field name ({@code @JoinColumn(name = ...)})
 * @param referencedProperty the property name on the associated entity ({@code referencedColumnName})
 */
public record CompositeJoinColumn(String localName, String referencedProperty) {
}

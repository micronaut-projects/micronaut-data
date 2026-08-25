package io.micronaut.data.nitrite.model;

import io.micronaut.core.annotation.Introspected;

/**
 * Non-{@code Serializable} POJO used to exercise the {@code INTROSPECTED_POJO} dispatch
 * strategy in the entity mapper. The {@code @Introspected} annotation causes a
 * compile-time {@link io.micronaut.core.beans.BeanIntrospection} to be generated,
 * which the mapper uses directly — no Serde codec and no {@code Serializable} required.
 */
@Introspected
public class CustomTag {

    private String key;
    private String value;

    /** Default constructor required by Serde. */
    public CustomTag() {
    }

    /**
     * Creates a tag with the given key and value.
     *
     * @param key the tag key
     * @param value the tag value
     */
    public CustomTag(String key, String value) {
        this.key = key;
        this.value = value;
    }

    /**
     * Returns the key.
     *
     * @return the key
     */
    public String getKey() {
        return key;
    }

    /**
     * Sets the key.
     *
     * @param key the key
     */
    public void setKey(String key) {
        this.key = key;
    }

    /**
     * Returns the value.
     *
     * @return the value
     */
    public String getValue() {
        return value;
    }

    /**
     * Sets the value.
     *
     * @param value the value
     */
    public void setValue(String value) {
        this.value = value;
    }
}

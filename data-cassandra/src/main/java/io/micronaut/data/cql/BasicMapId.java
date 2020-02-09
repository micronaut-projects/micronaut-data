package io.micronaut.data.cql;

import io.micronaut.core.util.ArgumentUtils;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class BasicMapId implements MapId {

    private final Map<String, Object> map = new HashMap<>();

    public BasicMapId() {
    }

    public BasicMapId(@Nonnull Map<String, Object> map) {
        ArgumentUtils.requireNonNull("map", map);
        this.map.putAll(map);
    }

    @Override
    public int size() {
        return map.size();
    }

    @Override
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public boolean containsKey(Object o) {
        return map.containsKey(o);
    }

    @Override
    public boolean containsValue(Object o) {
        return map.containsValue(o);
    }

    @Override
    public Object get(Object o) {
        return map.get(o);
    }

    @Override
    public Object put(String s, Object o) {
        return map.put(s, o);
    }

    @Override
    public Object remove(Object o) {
        return map.remove(o);
    }

    @Override
    public void putAll(Map<? extends String, ? extends Object> source) {
        map.putAll(source);
    }

    @Override
    public void clear() {
        map.clear();
    }

    @Override
    public Set<String> keySet() {
        return map.keySet();
    }

    @Override
    public Collection<Object> values() {
        return map.values();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return map.entrySet();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return map.equals(o);
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder("{");
        boolean first = false;
        for (Entry<String, Object> entry : map.entrySet()) {
            if (first) {
                first = false;
            } else {
                s.append(",");
            }
            s.append(entry.getKey()).append(" : ").append(entry.getValue());
        }
        return s.append(" }").toString();
    }

    @Override
    public int hashCode() {
        return map.hashCode();
    }
}

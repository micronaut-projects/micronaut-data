package io.micronaut.data.model.finders;

import java.util.regex.Pattern;

/**
 * Finder used to return a single result
 */
public class FindByFinder extends AbstractFindByFinder {

    private static final String METHOD_PATTERN = "((find|get|query|retrieve|read)By)([A-Z]\\w*)";

    public FindByFinder() {
        super(Pattern.compile(METHOD_PATTERN));
    }
}

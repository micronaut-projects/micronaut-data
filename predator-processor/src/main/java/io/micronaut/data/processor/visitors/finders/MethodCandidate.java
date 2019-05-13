package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.order.Ordered;
import io.micronaut.data.processor.visitors.MatchContext;
import io.micronaut.data.processor.visitors.MethodMatchContext;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;


/**
 * Implementation of dynamic finders.
 *
 * @author graeme rocher
 * @since 1.0
 */
public interface MethodCandidate extends Ordered {

    /**
     * The default position.
     */
    int DEFAULT_POSITION = 0;

    /**
     * Whether the given method name matches this finder.
     *
     * @param methodElement The method element. Never null.
     * @param matchContext The match context. Never null.
     * @return true if it does
     */
    boolean isMethodMatch(@NonNull MethodElement methodElement, @NonNull MatchContext matchContext);

    @Override
    default int getOrder() {
        return DEFAULT_POSITION;
    }

    /**
     * Builds the method info. The method {@link #isMethodMatch(MethodElement, MatchContext)} should be
     * invoked and checked prior to calling this method.
     *
     * @param matchContext The match context
     * @return The method info or null if it cannot be built. If the method info cannot be built an error will be reported to
     * the passed {@link VisitorContext}
     */
    @Nullable
    MethodMatchInfo buildMatchInfo(@NonNull MethodMatchContext matchContext);

}

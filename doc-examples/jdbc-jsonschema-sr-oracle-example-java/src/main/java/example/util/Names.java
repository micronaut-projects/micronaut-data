package example.util;

/**
 * Small helpers for naming conventions.
 */
public final class Names {
    private Names() {
    }

    /**
     * Convert CamelCase simple class name to UPPER_SNAKE (e.g., MoonPhase -> MOON_PHASE).
     */
    public static String toUpperSnake(String simpleName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < simpleName.length(); i++) {
            char c = simpleName.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('_');
            }
            sb.append(Character.toUpperCase(c));
        }
        return sb.toString();
    }
}

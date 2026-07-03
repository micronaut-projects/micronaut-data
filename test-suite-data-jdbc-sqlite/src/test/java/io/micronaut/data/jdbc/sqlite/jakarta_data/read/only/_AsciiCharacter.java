package io.micronaut.data.jdbc.sqlite.jakarta_data.read.only;

import jakarta.annotation.Generated;
import jakarta.data.Sort;
import jakarta.data.metamodel.SortableAttribute;
import jakarta.data.metamodel.StaticMetamodel;
import jakarta.data.metamodel.TextAttribute;

/**
 * This static metamodel class represents what an annotation processor-based approach
 * might generate.
 */
@Generated("ee.jakarta.tck.data.mock.generator")
@StaticMetamodel(AsciiCharacter.class)
public class _AsciiCharacter {
    public static final String ID = "id";
    public static final String HEXADECIMAL = "hexadecimal";
    public static final String NUMERICVALUE = "numericValue";

    public static final SortableAttribute<AsciiCharacter> id = new NumericAttr("id");
    public static final TextAttribute<AsciiCharacter> hexadecimal = new TextAttr("hexadecimal");
    public static final SortableAttribute<AsciiCharacter> isControl = new BooleanAttr("isControl");
    public static final SortableAttribute<AsciiCharacter> numericValue = new NumericAttr("numericValue");
    public static final TextAttribute<AsciiCharacter> thisCharacter = new TextAttr("thisCharacter");

    private static record BooleanAttr(String name, Sort<AsciiCharacter> asc, Sort<AsciiCharacter> desc)
            implements SortableAttribute<AsciiCharacter> {
        private BooleanAttr(String name) {
            this(name, Sort.asc(name), Sort.desc(name));
        }
    };

    private static record NumericAttr(String name, Sort<AsciiCharacter> asc, Sort<AsciiCharacter> desc)
            implements SortableAttribute<AsciiCharacter> {
        private NumericAttr(String name) {
            this(name, Sort.asc(name), Sort.desc(name));
        }
    };

    private static record TextAttr(String name, Sort<AsciiCharacter> asc, Sort<AsciiCharacter> ascIgnoreCase,
            Sort<AsciiCharacter> desc, Sort<AsciiCharacter> descIgnoreCase) implements TextAttribute<AsciiCharacter> {
        private TextAttr(String name) {
            this(name, Sort.asc(name), Sort.ascIgnoreCase(name), Sort.desc(name), Sort.descIgnoreCase(name));
        }
    };

    // Avoids the checkstyle error,
    // HideUtilityClassConstructor: Utility classes should not have a public or default constructor
    private _AsciiCharacter() {
    }
}

package io.micronaut.data.jdbc.sqlite.jakarta_data.read.only;

import jakarta.data.metamodel.Attribute;
import jakarta.data.metamodel.SortableAttribute;
import jakarta.data.metamodel.StaticMetamodel;
import jakarta.data.metamodel.TextAttribute;
import jakarta.data.metamodel.impl.AttributeRecord;
import jakarta.data.metamodel.impl.SortableAttributeRecord;
import jakarta.data.metamodel.impl.TextAttributeRecord;

/**
 * This static metamodel class tests what a user might explicitly provide,
 * in which case the Jakarta Data provider will need to initialize the attributes.
 */
@StaticMetamodel(AsciiCharacter.class)
public class _AsciiChar {
    public static final String ID = "id";
    public static final String HEXADECIMAL = "hexadecimal";
    public static final String NUMERICVALUE = "numericValue";

    public static final SortableAttribute<AsciiCharacter> id = new SortableAttributeRecord<>("id");
    public static final TextAttribute<AsciiCharacter> hexadecimal = new TextAttributeRecord<>("hexadecimal");
    public static final Attribute<AsciiCharacter> isControl = new AttributeRecord<>("isControl"); // user decided it didn't care about sorting for this one
    public static final SortableAttribute<AsciiCharacter> numericValue = new SortableAttributeRecord<>("numericValue");
    public static final TextAttribute<AsciiCharacter> thisCharacter = new TextAttributeRecord<>("thisCharacter");

    // Avoids the checkstyle error,
    // HideUtilityClassConstructor: Utility classes should not have a public or default constructor
    private _AsciiChar() {
    }
}

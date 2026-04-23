package io.micronaut.data.jdbc.sqlite.jakarta_data.read.only;


import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class AsciiCharactersPopulator implements Populator<AsciiCharacters> {

    public static AsciiCharactersPopulator get() {
        return new AsciiCharactersPopulator();
    }

    @Override
    public void populationLogic(AsciiCharacters repo) {
        List<AsciiCharacter> dictonary = new ArrayList<>();

        IntStream.range(1, 128) // Some databases don't support ASCII NULL character (0)
            .forEach(value -> {
                AsciiCharacter inst = new AsciiCharacter();

                inst.setId(value);
                inst.setNumericValue(value);
                inst.setHexadecimal(Integer.toHexString(value));
                inst.setThisCharacter((char) value);
                inst.setControl(Character.isISOControl((char) value));

                dictonary.add(inst);
            });

        repo.saveAll(dictonary);
    }

    @Override
    public boolean isPopulated(AsciiCharacters repo) {
        return repo.countByHexadecimalNotNull() == 127L;
    }
}

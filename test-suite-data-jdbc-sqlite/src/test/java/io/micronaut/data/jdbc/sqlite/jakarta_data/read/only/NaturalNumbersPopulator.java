package io.micronaut.data.jdbc.sqlite.jakarta_data.read.only;


import io.micronaut.data.jdbc.sqlite.jakarta_data.read.only.NaturalNumber.NumberType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class NaturalNumbersPopulator implements Populator<NaturalNumbers> {

    public static NaturalNumbersPopulator get() {
        return new NaturalNumbersPopulator();
    }

    @Override
    public boolean isPopulated(NaturalNumbers repo) {
       return repo.countAll() == 100L;
    }

    @Override
    public void populationLogic(NaturalNumbers repo) {
        List<NaturalNumber> dictionary = new ArrayList<>();

        IntStream.range(1, 101)
            .forEach(id -> {
                NaturalNumber inst = new NaturalNumber();

                boolean isOne = id == 1;
                boolean isOdd = id % 2 == 1;
                long sqrRoot = squareRoot(id);
                boolean isPrime = isOdd ? isPrime(id, sqrRoot) : (id == 2);
                NumberType numType = isOne ? NumberType.ONE : isPrime ? NumberType.PRIME : NumberType.COMPOSITE;

                inst.setId(id);
                inst.setOdd(isOdd);
                inst.setNumBitsRequired(bitsRequired(id));
                inst.setNumType(numType);
                inst.setNumTypeOrdinal(numType.ordinal());
                inst.setFloorOfSquareRoot(sqrRoot);

                dictionary.add(inst);
            });

        repo.saveAll(dictionary);
    }

    private static Short bitsRequired(int value) {
        return (short) (Math.floor(Math.log(value) / Math.log(2)) + 1);
    }

    private static long squareRoot(int value) {
        return (long) Math.floor(Math.sqrt(value));
    }

    private static boolean isPrime(int value, long largestPossibleFactor) {
        if(value == 1)
            return false;

        for(int i = 2; i <= largestPossibleFactor; i++) {
            if( value % i == 0 )
                return false;
        }
        return true;
    }
}

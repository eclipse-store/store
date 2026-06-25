package test.eclipse.store.library.types;

/*-
 * #%L
 * EclipseStore Integration Tests
 * %%
 * Copyright (C) 2023 - 2026 MicroStream Software
 * %%
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 * #L%
 */

import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BitSetData implements BinaryHandlerTestData {

    // basic value
    private BitSet basicBitSet;

    // corner-case fields
    private BitSet emptyBitSet;
    private BitSet nullBitSet;
    private BitSet singleBitZero;           // only bit 0 set
    private BitSet singleBitHigh;           // only a high-index bit set (e.g. 1023)
    private BitSet allBitsInWord;           // all 64 bits of the first word set (0..63)
    private BitSet crossWordBoundary;       // bits spanning two 64-bit words (62..65)
    private BitSet sparseBitSet;            // very few bits set, far apart
    private BitSet denseBitSet;             // many consecutive bits set
    private BitSet largeBitSet;             // large index range
    private BitSet fromByteArray;           // created via BitSet.valueOf(byte[])
    private BitSet fromLongArray;           // created via BitSet.valueOf(long[])
    private BitSet alternatingBits;         // alternating 1/0 pattern
    private BitSet evenBitsOnly;            // only even-index bits set
    private BitSet oddBitsOnly;             // only odd-index bits set
    private BitSet negatedPattern;          // result of flip on a range
    private BitSet andResult;               // result of AND operation
    private BitSet orResult;                // result of OR operation
    private BitSet xorResult;              // result of XOR operation
    private BitSet preAllocatedEmpty;       // new BitSet(1024) - pre-allocated but empty
    private BitSet clearedAfterSet;         // bits set then all cleared
    private BitSet maxIntBit;               // bit at Integer.MAX_VALUE / 64 boundary area

    @Override
    public BinaryHandlerTestData fillSampleData() {
        // basic value
        basicBitSet = new BitSet();
        basicBitSet.set(1);
        basicBitSet.set(3);
        basicBitSet.set(5);
        basicBitSet.set(7);

        // empty
        emptyBitSet = new BitSet();

        // null
        nullBitSet = null;

        // single bit at index 0
        singleBitZero = new BitSet();
        singleBitZero.set(0);

        // single bit at high index
        singleBitHigh = new BitSet();
        singleBitHigh.set(1023);

        // all bits in the first 64-bit word (indices 0..63)
        allBitsInWord = new BitSet();
        allBitsInWord.set(0, 64);

        // cross word boundary: bits 62, 63, 64, 65
        crossWordBoundary = new BitSet();
        crossWordBoundary.set(62);
        crossWordBoundary.set(63);
        crossWordBoundary.set(64);
        crossWordBoundary.set(65);

        // sparse: only a few bits far apart
        sparseBitSet = new BitSet();
        sparseBitSet.set(0);
        sparseBitSet.set(500);
        sparseBitSet.set(10_000);
        sparseBitSet.set(100_000);

        // dense: bits 0..999 all set
        denseBitSet = new BitSet();
        denseBitSet.set(0, 1000);

        // large: bits up to 100_000 set in a pattern (every 100th)
        largeBitSet = new BitSet();
        for (int i = 0; i < 100_000; i += 100) {
            largeBitSet.set(i);
        }

        // from byte array
        fromByteArray = BitSet.valueOf(new byte[]{(byte) 0xFF, 0x00, (byte) 0xAB, (byte) 0xCD});

        // from long array
        fromLongArray = BitSet.valueOf(new long[]{0xDEADBEEFCAFEBABEL, 0x0123456789ABCDEFL});

        // alternating bits: 0, 2, 4, 6, ... up to 127
        alternatingBits = new BitSet();
        for (int i = 0; i < 128; i += 2) {
            alternatingBits.set(i);
        }

        // even bits only in range 0..255
        evenBitsOnly = new BitSet();
        for (int i = 0; i < 256; i += 2) {
            evenBitsOnly.set(i);
        }

        // odd bits only in range 0..255
        oddBitsOnly = new BitSet();
        for (int i = 1; i < 256; i += 2) {
            oddBitsOnly.set(i);
        }

        // negated pattern: set 0..127 then flip all
        negatedPattern = new BitSet();
        negatedPattern.set(0, 128);
        negatedPattern.flip(0, 128);

        // AND result
        BitSet a = new BitSet();
        a.set(0, 64);
        BitSet b = new BitSet();
        b.set(32, 96);
        andResult = (BitSet) a.clone();
        andResult.and(b); // bits 32..63

        // OR result
        orResult = (BitSet) a.clone();
        orResult.or(b); // bits 0..95

        // XOR result
        xorResult = (BitSet) a.clone();
        xorResult.xor(b); // bits 0..31, 64..95

        // pre-allocated but empty
        preAllocatedEmpty = new BitSet(1024);

        // set bits then clear them all
        clearedAfterSet = new BitSet();
        clearedAfterSet.set(0, 512);
        clearedAfterSet.clear(0, 512);

        // bit near a large boundary (not Integer.MAX_VALUE itself, too much memory)
        maxIntBit = new BitSet();
        maxIntBit.set(65535); // 2^16 - 1, boundary of multiple words

        return this;
    }

    @Override
    public BinaryHandlerTestData updateSampleData() {
        // basic: add and remove bits
        basicBitSet.clear(1);
        basicBitSet.set(2);
        basicBitSet.set(100);

        // empty becomes non-empty
        emptyBitSet.set(42);

        // null becomes a value
        nullBitSet = new BitSet();
        nullBitSet.set(0);
        nullBitSet.set(1);

        // single bit zero: add more
        singleBitZero.set(63);
        singleBitZero.set(64);

        // single high bit: move to different high index
        singleBitHigh.clear(1023);
        singleBitHigh.set(2047);

        // all bits in word: clear half
        allBitsInWord.clear(0, 32);

        // cross word boundary: extend range
        crossWordBoundary.set(60, 68);

        // sparse: add more sparse bits
        sparseBitSet.set(50_000);
        sparseBitSet.set(200_000);

        // dense: clear a hole in the middle
        denseBitSet.clear(400, 600);

        // large: shift pattern
        for (int i = 50; i < 100_000; i += 100) {
            largeBitSet.set(i);
        }

        // from byte array: replace entirely
        fromByteArray = BitSet.valueOf(new byte[]{0x01, 0x02, 0x03, 0x04, 0x05});

        // from long array: replace
        fromLongArray = BitSet.valueOf(new long[]{Long.MAX_VALUE, Long.MIN_VALUE});

        // alternating: flip pattern
        alternatingBits.flip(0, 128);

        // even/odd: swap
        BitSet temp = (BitSet) evenBitsOnly.clone();
        evenBitsOnly = (BitSet) oddBitsOnly.clone();
        oddBitsOnly = temp;

        // negated pattern: set some bits
        negatedPattern.set(10, 20);

        // AND, OR, XOR: recompute with different operands
        BitSet c = new BitSet();
        c.set(0, 128);
        BitSet d = new BitSet();
        d.set(64, 192);
        andResult = (BitSet) c.clone();
        andResult.and(d);
        orResult = (BitSet) c.clone();
        orResult.or(d);
        xorResult = (BitSet) c.clone();
        xorResult.xor(d);

        // pre-allocated: set a bit
        preAllocatedEmpty.set(512);

        // cleared: set a different range
        clearedAfterSet.set(100, 200);

        // maxIntBit: move bit
        maxIntBit.clear(65535);
        maxIntBit.set(131071); // 2^17 - 1

        return this;
    }

    // --- getters ---
    public BitSet getBasicBitSet() { return basicBitSet; }
    public BitSet getEmptyBitSet() { return emptyBitSet; }
    public BitSet getNullBitSet() { return nullBitSet; }
    public BitSet getSingleBitZero() { return singleBitZero; }
    public BitSet getSingleBitHigh() { return singleBitHigh; }
    public BitSet getAllBitsInWord() { return allBitsInWord; }
    public BitSet getCrossWordBoundary() { return crossWordBoundary; }
    public BitSet getSparseBitSet() { return sparseBitSet; }
    public BitSet getDenseBitSet() { return denseBitSet; }
    public BitSet getLargeBitSet() { return largeBitSet; }
    public BitSet getFromByteArray() { return fromByteArray; }
    public BitSet getFromLongArray() { return fromLongArray; }
    public BitSet getAlternatingBits() { return alternatingBits; }
    public BitSet getEvenBitsOnly() { return evenBitsOnly; }
    public BitSet getOddBitsOnly() { return oddBitsOnly; }
    public BitSet getNegatedPattern() { return negatedPattern; }
    public BitSet getAndResult() { return andResult; }
    public BitSet getOrResult() { return orResult; }
    public BitSet getXorResult() { return xorResult; }
    public BitSet getPreAllocatedEmpty() { return preAllocatedEmpty; }
    public BitSet getClearedAfterSet() { return clearedAfterSet; }
    public BitSet getMaxIntBit() { return maxIntBit; }

    @Override
    public void proveResults(Object o) {
        assertNotNull(o);
        BitSetData copy = (BitSetData) o;

        assertAll("BitSetData",
                () -> assertBitSetNullSafe(this.getBasicBitSet(), copy.getBasicBitSet(), "basicBitSet"),
                () -> assertBitSetNullSafe(this.getEmptyBitSet(), copy.getEmptyBitSet(), "emptyBitSet"),
                () -> assertBitSetNullSafe(this.getNullBitSet(), copy.getNullBitSet(), "nullBitSet"),
                () -> assertBitSetNullSafe(this.getSingleBitZero(), copy.getSingleBitZero(), "singleBitZero"),
                () -> assertBitSetNullSafe(this.getSingleBitHigh(), copy.getSingleBitHigh(), "singleBitHigh"),
                () -> assertBitSetNullSafe(this.getAllBitsInWord(), copy.getAllBitsInWord(), "allBitsInWord"),
                () -> assertBitSetNullSafe(this.getCrossWordBoundary(), copy.getCrossWordBoundary(), "crossWordBoundary"),
                () -> assertBitSetNullSafe(this.getSparseBitSet(), copy.getSparseBitSet(), "sparseBitSet"),
                () -> assertBitSetNullSafe(this.getDenseBitSet(), copy.getDenseBitSet(), "denseBitSet"),
                () -> assertBitSetNullSafe(this.getLargeBitSet(), copy.getLargeBitSet(), "largeBitSet"),
                () -> assertBitSetNullSafe(this.getFromByteArray(), copy.getFromByteArray(), "fromByteArray"),
                () -> assertBitSetNullSafe(this.getFromLongArray(), copy.getFromLongArray(), "fromLongArray"),
                () -> assertBitSetNullSafe(this.getAlternatingBits(), copy.getAlternatingBits(), "alternatingBits"),
                () -> assertBitSetNullSafe(this.getEvenBitsOnly(), copy.getEvenBitsOnly(), "evenBitsOnly"),
                () -> assertBitSetNullSafe(this.getOddBitsOnly(), copy.getOddBitsOnly(), "oddBitsOnly"),
                () -> assertBitSetNullSafe(this.getNegatedPattern(), copy.getNegatedPattern(), "negatedPattern"),
                () -> assertBitSetNullSafe(this.getAndResult(), copy.getAndResult(), "andResult"),
                () -> assertBitSetNullSafe(this.getOrResult(), copy.getOrResult(), "orResult"),
                () -> assertBitSetNullSafe(this.getXorResult(), copy.getXorResult(), "xorResult"),
                () -> assertBitSetNullSafe(this.getPreAllocatedEmpty(), copy.getPreAllocatedEmpty(), "preAllocatedEmpty"),
                () -> assertBitSetNullSafe(this.getClearedAfterSet(), copy.getClearedAfterSet(), "clearedAfterSet"),
                () -> assertBitSetNullSafe(this.getMaxIntBit(), copy.getMaxIntBit(), "maxIntBit")
        );
    }

    /**
     * Null-safe comparison of two BitSet instances.
     * When expected is null, actual must also be null.
     * When expected is non-null, verifies equality, cardinality, length, and underlying long array.
     */
    private static void assertBitSetNullSafe(BitSet expected, BitSet actual, String label) {
        if (expected == null) {
            assertNull(actual, label + " should be null");
            return;
        }
        assertNotNull(actual, label + " should not be null");
        assertEquals(expected.cardinality(), actual.cardinality(), label + " cardinality");
        assertEquals(expected.length(), actual.length(), label + " length");
        assertEquals(expected, actual, label + " equals");
        // also verify the underlying long[] representation is identical
        assertTrue(java.util.Arrays.equals(expected.toLongArray(), actual.toLongArray()),
                label + " toLongArray content");
    }

    @Override
    public String toString()
    {
        return "BitSetData{" +
                "basicBitSet=" + basicBitSet +
                ", emptyBitSet=" + emptyBitSet +
                ", nullBitSet=" + nullBitSet +
                ", singleBitZero=" + singleBitZero +
                ", singleBitHigh=" + singleBitHigh +
                ", allBitsInWord=" + allBitsInWord +
                ", crossWordBoundary=" + crossWordBoundary +
                ", sparseBitSet=" + sparseBitSet +
                ", denseBitSet=" + denseBitSet +
                ", largeBitSet=" + largeBitSet +
                ", fromByteArray=" + fromByteArray +
                ", fromLongArray=" + fromLongArray +
                ", alternatingBits=" + alternatingBits +
                ", evenBitsOnly=" + evenBitsOnly +
                ", oddBitsOnly=" + oddBitsOnly +
                ", negatedPattern=" + negatedPattern +
                ", andResult=" + andResult +
                ", orResult=" + orResult +
                ", xorResult=" + xorResult +
                ", preAllocatedEmpty=" + preAllocatedEmpty +
                ", clearedAfterSet=" + clearedAfterSet +
                ", maxIntBit=" + maxIntBit +
                '}';
    }
}


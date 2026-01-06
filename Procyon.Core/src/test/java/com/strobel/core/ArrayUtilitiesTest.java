package com.strobel.core;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class ArrayUtilitiesTest {

    @Test
    public void testConstructorIsUnreachable() throws Exception {
        final Constructor<ArrayUtilities> constructor = ArrayUtilities.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        try {
            constructor.newInstance();
            Assert.fail("Expected the private constructor to throw.");
        }
        catch (final InvocationTargetException exception) {
            Assert.assertNotNull(exception.getCause());
        }
    }

    @Test
    public void testIsArrayCreateCreateAnyRangeCopyOfCopyOfRangeMakeArrayTypeAndUnmodifiableList() {
        Assert.assertFalse(ArrayUtilities.isArray(null));
        Assert.assertFalse(ArrayUtilities.isArray("not an array"));
        Assert.assertTrue(ArrayUtilities.isArray(new int[] { 1, 2 }));

        final String[] createdStrings = ArrayUtilities.create(String.class, 2);
        Assert.assertEquals(2, createdStrings.length);
        Assert.assertEquals(String[].class, createdStrings.getClass());

        final Object createdAny = ArrayUtilities.createAny(int.class, 3);
        Assert.assertTrue(createdAny instanceof int[]);
        Assert.assertEquals(3, ((int[]) createdAny).length);

        Assert.assertArrayEquals(new int[0], ArrayUtilities.range(10, 0));
        Assert.assertArrayEquals(new int[] { 5, 6, 7 }, ArrayUtilities.range(5, 3));

        final String[] strings = new String[] { "a", "b", "c" };

        final Object copyOfSameTypeShorter = ArrayUtilities.copyOf(strings, 2, String[].class);
        Assert.assertArrayEquals(new String[] { "a", "b" }, (String[]) copyOfSameTypeShorter);

        final Object copyOfSameTypeLonger = ArrayUtilities.copyOf(strings, 5, String[].class);
        Assert.assertArrayEquals(new String[] { "a", "b", "c", null, null }, (String[]) copyOfSameTypeLonger);

        final Object copyOfAsObjectArray = ArrayUtilities.copyOf(strings, 2, Object[].class);
        Assert.assertEquals(Object[].class, copyOfAsObjectArray.getClass());
        Assert.assertArrayEquals(new Object[] { "a", "b" }, (Object[]) copyOfAsObjectArray);

        final Object copyOfImplicitType = ArrayUtilities.copyOf(strings, 2);
        Assert.assertArrayEquals(new String[] { "a", "b" }, (String[]) copyOfImplicitType);

        final Object primitiveCopyOf = ArrayUtilities.copyOf(new int[] { 1, 2, 3 }, 5);
        Assert.assertTrue(primitiveCopyOf instanceof int[]);
        Assert.assertArrayEquals(new int[] { 1, 2, 3, 0, 0 }, (int[]) primitiveCopyOf);

        final Object copyOfRangeImplicitType = ArrayUtilities.copyOfRange(strings, 1, 3);
        Assert.assertArrayEquals(new String[] { "b", "c" }, (String[]) copyOfRangeImplicitType);

        final Object copyOfRangeObjectArray = ArrayUtilities.copyOfRange(strings, 0, 2, Object[].class);
        Assert.assertEquals(Object[].class, copyOfRangeObjectArray.getClass());
        Assert.assertArrayEquals(new Object[] { "a", "b" }, (Object[]) copyOfRangeObjectArray);

        try {
            ArrayUtilities.copyOfRange(strings, 2, 1);
            Assert.fail("Expected IllegalArgumentException when from is greater than to.");
        }
        catch (final IllegalArgumentException ignored) {
        }

        final Class<String[]> firstArrayType = ArrayUtilities.makeArrayType(String.class);
        final Class<String[]> secondArrayType = ArrayUtilities.makeArrayType(String.class);
        Assert.assertEquals(String[].class, firstArrayType);
        Assert.assertSame(firstArrayType, secondArrayType);

        final List<String> unmodifiableList = ArrayUtilities.asUnmodifiableList("x", "y");
        Assert.assertEquals(2, unmodifiableList.size());
        Assert.assertEquals("x", unmodifiableList.get(0));
        Assert.assertEquals("y", unmodifiableList.get(1));
    }

    @Test
    public void testObjectArrayCopySearchAndManipulation() {
        final String[] source = new String[] { "a", "b", "c" };

        final String[] copyWithNullTarget = ArrayUtilities.copy(source, (String[]) null);
        Assert.assertArrayEquals(source, copyWithNullTarget);

        final String[] copyWithNullTargetOffsetZeroReturnsFullCopy = ArrayUtilities.copy(source, 1, null, 0, 1);
        Assert.assertArrayEquals(source, copyWithNullTargetOffsetZeroReturnsFullCopy);

        final String[] copyWithNullTargetOffsetNonZero = ArrayUtilities.copy(source, 1, null, 1, 2);
        Assert.assertArrayEquals(new String[] { null, "b", "c" }, copyWithNullTargetOffsetNonZero);

        final String[] shortTargetOffsetZero = new String[] { "z" };
        final String[] grownTargetOffsetZero = ArrayUtilities.copy(source, 0, shortTargetOffsetZero, 0, 2);
        Assert.assertArrayEquals(new String[] { "a", "b" }, grownTargetOffsetZero);

        final String[] shortTargetOffsetNonZero = new String[] { "p" };
        final String[] grownTargetOffsetNonZero = ArrayUtilities.copy(source, 0, shortTargetOffsetNonZero, 1, 2);
        Assert.assertArrayEquals(new String[] { "p", "a", "b" }, grownTargetOffsetNonZero);

        final String[] largeTarget = new String[] { "q", "q", "q" };
        final String[] reusedTarget = ArrayUtilities.copy(source, 0, largeTarget, 0, 2);
        Assert.assertSame(largeTarget, reusedTarget);
        Assert.assertArrayEquals(new String[] { "a", "b", "q" }, reusedTarget);

        Assert.assertFalse(ArrayUtilities.rangeEquals(source, new String[] { "a" }, -1, 1));
        Assert.assertTrue(ArrayUtilities.rangeEquals(source, source, 0, 3));
        Assert.assertFalse(ArrayUtilities.rangeEquals(source, new String[] { "a", "x", "c" }, 0, 3));
        Assert.assertTrue(ArrayUtilities.rangeEquals(source, new String[] { "a", "b", "c" }, 0, 3));

        final String[] withNulls = new String[] { null, "a", null };
        Assert.assertEquals(0, ArrayUtilities.indexOf(withNulls, null));
        Assert.assertEquals(-1, ArrayUtilities.indexOf(withNulls, "missing"));
        Assert.assertEquals(2, ArrayUtilities.lastIndexOf(withNulls, null));
        Assert.assertEquals(1, ArrayUtilities.lastIndexOf(withNulls, "a"));

        Assert.assertTrue(ArrayUtilities.contains(source, "b"));
        Assert.assertFalse(ArrayUtilities.contains(source, "missing"));

        Assert.assertArrayEquals(new String[] { "a", "x", "b", "c" }, ArrayUtilities.insert(source, 1, "x"));
        Assert.assertArrayEquals(new String[] { "a", "b", "c", "x" }, ArrayUtilities.insert(source, 3, "x"));

        final String[] insertVarargsNoValuesEmpty = ArrayUtilities.insert(source, 1, new String[0]);
        Assert.assertSame(source, insertVarargsNoValuesEmpty);

        final String[] insertVarargsNoValuesNull = ArrayUtilities.insert(source, 1, (String[]) null);
        Assert.assertSame(source, insertVarargsNoValuesNull);

        Assert.assertArrayEquals(
            new String[] { "a", "x", "y", "b", "c" },
            ArrayUtilities.insert(source, 1, "x", "y")
        );

        Assert.assertArrayEquals(new String[] { "a", "b", "c", "x" }, ArrayUtilities.append(source, "x"));
        Assert.assertArrayEquals(new String[] { "x", "a", "b", "c" }, ArrayUtilities.prepend(source, "x"));

        final String[] appendedFromNull = ArrayUtilities.append(null, "x");
        Assert.assertArrayEquals(new String[] { "x" }, appendedFromNull);

        try {
            ArrayUtilities.append(null, (String) null);
            Assert.fail("Expected IllegalArgumentException when both array and value are null.");
        }
        catch (final IllegalArgumentException ignored) {
        }

        final String[] appendedVarargsFromNull = ArrayUtilities.append(null, "x", "y");
        Assert.assertArrayEquals(new String[] { "x", "y" }, appendedVarargsFromNull);

        try {
            ArrayUtilities.append(null, (String[]) null);
            Assert.fail("Expected IllegalArgumentException when array is null and values are null.");
        }
        catch (final IllegalArgumentException ignored) {
        }

        final String[] prependedFromNull = ArrayUtilities.prepend(null, "x");
        Assert.assertArrayEquals(new String[] { "x" }, prependedFromNull);

        try {
            ArrayUtilities.prepend(null, (String) null);
            Assert.fail("Expected IllegalArgumentException when both array and value are null.");
        }
        catch (final IllegalArgumentException ignored) {
        }

        final String[] prependedVarargsFromNull = ArrayUtilities.prepend(null, "x", "y");
        Assert.assertArrayEquals(new String[] { "x", "y" }, prependedVarargsFromNull);

        try {
            ArrayUtilities.prepend(null, (String[]) null);
            Assert.fail("Expected IllegalArgumentException when array is null and values are null.");
        }
        catch (final IllegalArgumentException ignored) {
        }

        Assert.assertArrayEquals(new String[] { "a", "c" }, ArrayUtilities.remove(source, 1));
        Assert.assertArrayEquals(new String[] { "a", "b" }, ArrayUtilities.remove(source, 2));

        final String[] removedSingle = ArrayUtilities.remove(new String[] { "only" }, 0);
        Assert.assertEquals(0, removedSingle.length);
        Assert.assertEquals(String[].class, removedSingle.getClass());

        Assert.assertTrue(ArrayUtilities.isNullOrEmpty((String[]) null));
        Assert.assertTrue(ArrayUtilities.isNullOrEmpty(new String[0]));
        Assert.assertFalse(ArrayUtilities.isNullOrEmpty(new String[] { "x" }));

        final String[] removeAllNoMatches = new String[] { "a", "b", "c" };
        Assert.assertSame(removeAllNoMatches, ArrayUtilities.removeAll(removeAllNoMatches, "missing"));

        final String[] removeAllOneMatch = new String[] { "a", "b", "c", "b" };
        Assert.assertArrayEquals(new String[] { "a", "c", "b" }, ArrayUtilities.removeAll(removeAllOneMatch, "b", "missing"));

        final String[] removeFirstNotFound = new String[] { "a", "b" };
        Assert.assertSame(removeFirstNotFound, ArrayUtilities.removeFirst(removeFirstNotFound, "missing"));

        final String[] removeLastNotFound = new String[] { "a", "b" };
        Assert.assertSame(removeLastNotFound, ArrayUtilities.removeLast(removeLastNotFound, "missing"));

        Assert.assertArrayEquals(new String[] { "a", "c", "b" }, ArrayUtilities.removeFirst(removeAllOneMatch, "b"));
        Assert.assertArrayEquals(new String[] { "a", "b", "c" }, ArrayUtilities.removeLast(removeAllOneMatch, "b"));

        final String[] retainAllNoValuesSpecified = new String[] { "a", "b" };
        Assert.assertSame(retainAllNoValuesSpecified, ArrayUtilities.retainAll(retainAllNoValuesSpecified));

        final String[] retainAllNoMatches = ArrayUtilities.retainAll(new String[] { "a", "b" }, "missing");
        Assert.assertEquals(0, retainAllNoMatches.length);
        Assert.assertEquals(String[].class, retainAllNoMatches.getClass());

        final String[] retainAllWithMatches = ArrayUtilities.retainAll(new String[] { "a", "b", "c" }, "b", "missing");
        Assert.assertArrayEquals(new String[] { "b" }, retainAllWithMatches);

        final String[] unionNoValues = new String[] { "a", "b" };
        Assert.assertSame(unionNoValues, ArrayUtilities.union(unionNoValues));

        final String[] unionWithNoMatches = ArrayUtilities.union(new String[] { "a", "b" }, "c");
        Assert.assertArrayEquals(new String[] { "a", "b", "c" }, unionWithNoMatches);

        final String[] unionWithSomeMatches = ArrayUtilities.union(new String[] { "a", "b" }, "b", "c");
        Assert.assertArrayEquals(new String[] { "a", "b", "c" }, unionWithSomeMatches);
    }

    @Test
    public void testBooleanPrimitiveSpecializations() {
        Assert.assertTrue(ArrayUtilities.isNullOrEmpty((boolean[]) null));
        Assert.assertTrue(ArrayUtilities.isNullOrEmpty(new boolean[0]));
        Assert.assertFalse(ArrayUtilities.isNullOrEmpty(new boolean[] { true }));

        final boolean[] source = new boolean[] { true, false, true };

        Assert.assertArrayEquals(source, ArrayUtilities.copy(source, (boolean[]) null));
        Assert.assertArrayEquals(new boolean[] { false, true }, ArrayUtilities.copy(source, 1, null, 0, 2));

        final boolean[] shortTargetOffsetZero = new boolean[] { true };
        Assert.assertArrayEquals(new boolean[] { false, true }, ArrayUtilities.copy(source, 1, shortTargetOffsetZero, 0, 2));

        final boolean[] shortTargetOffsetNonZero = new boolean[] { true };
        Assert.assertArrayEquals(new boolean[] { true, false }, ArrayUtilities.copy(source, 1, shortTargetOffsetNonZero, 1, 1));

        final boolean[] largeTarget = new boolean[] { false, false, false };
        final boolean[] reused = ArrayUtilities.copy(source, 1, largeTarget, 0, 2);
        Assert.assertSame(largeTarget, reused);
        Assert.assertArrayEquals(new boolean[] { false, true, false }, reused);

        Assert.assertFalse(ArrayUtilities.rangeEquals(source, new boolean[] { true }, -1, 1));
        Assert.assertTrue(ArrayUtilities.rangeEquals(source, source, 0, 3));
        Assert.assertFalse(ArrayUtilities.rangeEquals(new boolean[] { true, false }, new boolean[] { true, true }, 0, 2));
        Assert.assertTrue(ArrayUtilities.rangeEquals(new boolean[] { true, false }, new boolean[] { true, false }, 0, 2));

        Assert.assertTrue(ArrayUtilities.contains(source, false));
        Assert.assertFalse(ArrayUtilities.contains(new boolean[] { true, true }, false));

        Assert.assertEquals(1, ArrayUtilities.indexOf(source, false));
        Assert.assertEquals(-1, ArrayUtilities.indexOf(new boolean[] { true, true }, false));
        Assert.assertEquals(2, ArrayUtilities.lastIndexOf(source, true));
        Assert.assertEquals(-1, ArrayUtilities.lastIndexOf(new boolean[] { false, false }, true));

        Assert.assertArrayEquals(new boolean[] { true }, ArrayUtilities.append((boolean[]) null, true));
        Assert.assertArrayEquals(new boolean[] { true, false, true, false }, ArrayUtilities.append(source, false));
        Assert.assertNull(ArrayUtilities.append((boolean[]) null, (boolean[]) null));
        Assert.assertArrayEquals(new boolean[] { true, false }, ArrayUtilities.append((boolean[]) null, true, false));
        Assert.assertArrayEquals(new boolean[] { true, false, true, true, false }, ArrayUtilities.append(source, true, false));

        Assert.assertArrayEquals(new boolean[] { true }, ArrayUtilities.prepend((boolean[]) null, true));
        Assert.assertArrayEquals(new boolean[] { false, true, false, true }, ArrayUtilities.prepend(source, false));
        Assert.assertNull(ArrayUtilities.prepend((boolean[]) null, (boolean[]) null));
        Assert.assertArrayEquals(new boolean[] { true, false }, ArrayUtilities.prepend((boolean[]) null, true, false));

        Assert.assertEquals(0, ArrayUtilities.remove(new boolean[] { true }, 0).length);
        Assert.assertArrayEquals(new boolean[] { true, true }, ArrayUtilities.remove(source, 1));
        Assert.assertArrayEquals(new boolean[] { true, false }, ArrayUtilities.remove(source, 2));

        Assert.assertArrayEquals(new boolean[] { false, true, false }, ArrayUtilities.insert(new boolean[] { false, false }, 1, true));
        Assert.assertArrayEquals(new boolean[] { false, false, true }, ArrayUtilities.insert(new boolean[] { false, false }, 2, true));

        final boolean[] insertNoValuesNull = ArrayUtilities.insert(source, 1, (boolean[]) null);
        Assert.assertSame(source, insertNoValuesNull);

        final boolean[] insertNoValuesEmpty = ArrayUtilities.insert(source, 1, new boolean[0]);
        Assert.assertSame(source, insertNoValuesEmpty);

        Assert.assertArrayEquals(new boolean[] { true, false, false, true, true }, ArrayUtilities.insert(source, 2, false, true));
    }

    @Test
    public void testCharPrimitiveSpecializations() {
        Assert.assertTrue(ArrayUtilities.isNullOrEmpty((char[]) null));
        Assert.assertTrue(ArrayUtilities.isNullOrEmpty(new char[0]));
        Assert.assertFalse(ArrayUtilities.isNullOrEmpty(new char[] { 'a' }));

        final char[] source = new char[] { 'a', 'b', 'c' };

        Assert.assertArrayEquals(source, ArrayUtilities.copy(source, (char[]) null));
        Assert.assertArrayEquals(new char[] { 'b', 'c' }, ArrayUtilities.copy(source, 1, null, 0, 2));
        Assert.assertArrayEquals(new char[] { '\0', '\0', 'b' }, ArrayUtilities.copy(source, 1, null, 2, 1));

        final char[] shortTargetOffsetZero = new char[] { 'z' };
        Assert.assertArrayEquals(new char[] { 'b', 'c' }, ArrayUtilities.copy(source, 1, shortTargetOffsetZero, 0, 2));

        final char[] shortTargetOffsetNonZero = new char[] { 'p' };
        Assert.assertArrayEquals(new char[] { 'p', 'b' }, ArrayUtilities.copy(source, 1, shortTargetOffsetNonZero, 1, 1));

        final char[] largeTarget = new char[] { 'x', 'x', 'x' };
        final char[] reused = ArrayUtilities.copy(source, 1, largeTarget, 0, 2);
        Assert.assertSame(largeTarget, reused);
        Assert.assertArrayEquals(new char[] { 'b', 'c', 'x' }, reused);

        Assert.assertFalse(ArrayUtilities.rangeEquals(source, new char[] { 'a' }, -1, 1));
        Assert.assertTrue(ArrayUtilities.rangeEquals(source, source, 0, 3));
        Assert.assertFalse(ArrayUtilities.rangeEquals(new char[] { 'a', 'b' }, new char[] { 'a', 'x' }, 0, 2));
        Assert.assertTrue(ArrayUtilities.rangeEquals(new char[] { 'a', 'b' }, new char[] { 'a', 'b' }, 0, 2));

        Assert.assertTrue(ArrayUtilities.contains(source, 'b'));
        Assert.assertFalse(ArrayUtilities.contains(new char[] { 'a' }, 'z'));

        Assert.assertEquals(1, ArrayUtilities.indexOf(source, 'b'));
        Assert.assertEquals(-1, ArrayUtilities.indexOf(source, 'z'));
        Assert.assertEquals(2, ArrayUtilities.lastIndexOf(source, 'c'));
        Assert.assertEquals(-1, ArrayUtilities.lastIndexOf(source, 'z'));

        Assert.assertArrayEquals(new char[] { 'a' }, ArrayUtilities.append((char[]) null, 'a'));
        Assert.assertNull(ArrayUtilities.append((char[]) null, (char[]) null));
        Assert.assertArrayEquals(new char[] { 'a', 'b' }, ArrayUtilities.append((char[]) null, 'a', 'b'));
        Assert.assertArrayEquals(new char[] { 'a', 'b', 'c', 'd' }, ArrayUtilities.append(source, 'd'));
        Assert.assertArrayEquals(new char[] { 'a', 'b', 'c', 'x', 'y' }, ArrayUtilities.append(source, 'x', 'y'));

        Assert.assertArrayEquals(new char[] { 'a' }, ArrayUtilities.prepend((char[]) null, 'a'));
        Assert.assertNull(ArrayUtilities.prepend((char[]) null, (char[]) null));
        Assert.assertArrayEquals(new char[] { 'a', 'b' }, ArrayUtilities.prepend((char[]) null, 'a', 'b'));
        Assert.assertArrayEquals(new char[] { 'z', 'a', 'b', 'c' }, ArrayUtilities.prepend(source, 'z'));
        Assert.assertArrayEquals(new char[] { 'x', 'y', 'a', 'b', 'c' }, ArrayUtilities.prepend(source, 'x', 'y'));

        Assert.assertEquals(0, ArrayUtilities.remove(new char[] { 'a' }, 0).length);
        Assert.assertArrayEquals(new char[] { 'a', 'c' }, ArrayUtilities.remove(source, 1));

        Assert.assertArrayEquals(new char[] { 'a', 'x', 'b', 'c' }, ArrayUtilities.insert(source, 1, 'x'));
        Assert.assertArrayEquals(new char[] { 'a', 'b', 'c', 'x' }, ArrayUtilities.insert(source, 3, 'x'));

        final char[] insertNoValuesNull = ArrayUtilities.insert(source, 1, (char[]) null);
        Assert.assertSame(source, insertNoValuesNull);

        final char[] insertNoValuesEmpty = ArrayUtilities.insert(source, 1, new char[0]);
        Assert.assertSame(source, insertNoValuesEmpty);

        Assert.assertArrayEquals(new char[] { 'a', 'b', 'x', 'y', 'c' }, ArrayUtilities.insert(source, 2, 'x', 'y'));
    }

    @Test
    public void testBytePrimitiveSpecializations() {
        Assert.assertTrue(ArrayUtilities.isNullOrEmpty((byte[]) null));
        Assert.assertTrue(ArrayUtilities.isNullOrEmpty(new byte[0]));
        Assert.assertFalse(ArrayUtilities.isNullOrEmpty(new byte[] { 1 }));

        final byte[] source = new byte[] { 1, 2, 3 };

        Assert.assertArrayEquals(source, ArrayUtilities.copy(source, (byte[]) null));
        Assert.assertArrayEquals(new byte[] { 2, 3 }, ArrayUtilities.copy(source, 1, null, 0, 2));
        Assert.assertArrayEquals(new byte[] { 0, 0, 2 }, ArrayUtilities.copy(source, 1, null, 2, 1));

        final byte[] shortTargetOffsetZero = new byte[] { 9 };
        Assert.assertArrayEquals(new byte[] { 2, 3 }, ArrayUtilities.copy(source, 1, shortTargetOffsetZero, 0, 2));

        final byte[] shortTargetOffsetNonZero = new byte[] { 9 };
        Assert.assertArrayEquals(new byte[] { 9, 2 }, ArrayUtilities.copy(source, 1, shortTargetOffsetNonZero, 1, 1));

        final byte[] largeTarget = new byte[] { 8, 8, 8 };
        final byte[] reused = ArrayUtilities.copy(source, 1, largeTarget, 0, 2);
        Assert.assertSame(largeTarget, reused);
        Assert.assertArrayEquals(new byte[] { 2, 3, 8 }, reused);

        Assert.assertFalse(ArrayUtilities.rangeEquals(source, new byte[] { 1 }, -1, 1));
        Assert.assertTrue(ArrayUtilities.rangeEquals(source, source, 0, 3));
        Assert.assertFalse(ArrayUtilities.rangeEquals(new byte[] { 1, 2 }, new byte[] { 1, 9 }, 0, 2));
        Assert.assertTrue(ArrayUtilities.rangeEquals(new byte[] { 1, 2 }, new byte[] { 1, 2 }, 0, 2));

        Assert.assertTrue(ArrayUtilities.contains(source, (byte) 2));
        Assert.assertFalse(ArrayUtilities.contains(source, (byte) 9));

        Assert.assertEquals(1, ArrayUtilities.indexOf(source, (byte) 2));
        Assert.assertEquals(-1, ArrayUtilities.indexOf(source, (byte) 9));
        Assert.assertEquals(2, ArrayUtilities.lastIndexOf(source, (byte) 3));
        Assert.assertEquals(-1, ArrayUtilities.lastIndexOf(source, (byte) 9));

        Assert.assertArrayEquals(new byte[] { 1 }, ArrayUtilities.append((byte[]) null, (byte) 1));
        Assert.assertNull(ArrayUtilities.append((byte[]) null, (byte[]) null));
        Assert.assertArrayEquals(new byte[] { 1, 2 }, ArrayUtilities.append((byte[]) null, (byte) 1, (byte) 2));
        Assert.assertArrayEquals(new byte[] { 1, 2, 3, 4 }, ArrayUtilities.append(source, (byte) 4));
        Assert.assertArrayEquals(new byte[] { 1, 2, 3, 4, 5 }, ArrayUtilities.append(source, (byte) 4, (byte) 5));

        Assert.assertArrayEquals(new byte[] { 1 }, ArrayUtilities.prepend((byte[]) null, (byte) 1));
        Assert.assertNull(ArrayUtilities.prepend((byte[]) null, (byte[]) null));
        Assert.assertArrayEquals(new byte[] { 1, 2 }, ArrayUtilities.prepend((byte[]) null, (byte) 1, (byte) 2));
        Assert.assertArrayEquals(new byte[] { 9, 1, 2, 3 }, ArrayUtilities.prepend(source, (byte) 9));
        Assert.assertArrayEquals(new byte[] { 8, 9, 1, 2, 3 }, ArrayUtilities.prepend(source, (byte) 8, (byte) 9));

        Assert.assertEquals(0, ArrayUtilities.remove(new byte[] { 1 }, 0).length);
        Assert.assertArrayEquals(new byte[] { 1, 3 }, ArrayUtilities.remove(source, 1));

        Assert.assertArrayEquals(new byte[] { 1, 9, 2, 3 }, ArrayUtilities.insert(source, 1, (byte) 9));
        Assert.assertArrayEquals(new byte[] { 1, 2, 3, 9 }, ArrayUtilities.insert(source, 3, (byte) 9));

        final byte[] insertNoValuesNull = ArrayUtilities.insert(source, 1, (byte[]) null);
        Assert.assertSame(source, insertNoValuesNull);

        final byte[] insertNoValuesEmpty = ArrayUtilities.insert(source, 1, new byte[0]);
        Assert.assertSame(source, insertNoValuesEmpty);

        Assert.assertArrayEquals(new byte[] { 1, 2, 9, 8, 3 }, ArrayUtilities.insert(source, 2, (byte) 9, (byte) 8));
    }

    @Test
    public void testShortPrimitiveSpecializations() {
        Assert.assertTrue(ArrayUtilities.isNullOrEmpty((short[]) null));
        Assert.assertTrue(ArrayUtilities.isNullOrEmpty(new short[0]));
        Assert.assertFalse(ArrayUtilities.isNullOrEmpty(new short[] { 1 }));

        final short[] source = new short[] { 1, 2, 3 };

        Assert.assertArrayEquals(source, ArrayUtilities.copy(source, (short[]) null));
        Assert.assertArrayEquals(new short[] { 2, 3 }, ArrayUtilities.copy(source, 1, null, 0, 2));
        Assert.assertArrayEquals(new short[] { 0, 0, 2 }, ArrayUtilities.copy(source, 1, null, 2, 1));

        final short[] shortTargetOffsetZero = new short[] { 9 };
        Assert.assertArrayEquals(new short[] { 2, 3 }, ArrayUtilities.copy(source, 1, shortTargetOffsetZero, 0, 2));

        final short[] shortTargetOffsetNonZero = new short[] { 9 };
        Assert.assertArrayEquals(new short[] { 9, 2 }, ArrayUtilities.copy(source, 1, shortTargetOffsetNonZero, 1, 1));

        final short[] largeTarget = new short[] { 8, 8, 8 };
        final short[] reused = ArrayUtilities.copy(source, 1, largeTarget, 0, 2);
        Assert.assertSame(largeTarget, reused);
        Assert.assertArrayEquals(new short[] { 2, 3, 8 }, reused);

        Assert.assertFalse(ArrayUtilities.rangeEquals(source, new short[] { 1 }, -1, 1));
        Assert.assertTrue(ArrayUtilities.rangeEquals(source, source, 0, 3));
        Assert.assertFalse(ArrayUtilities.rangeEquals(new short[] { 1, 2 }, new short[] { 1, 9 }, 0, 2));
        Assert.assertTrue(ArrayUtilities.rangeEquals(new short[] { 1, 2 }, new short[] { 1, 2 }, 0, 2));

        Assert.assertTrue(ArrayUtilities.contains(source, (short) 2));
        Assert.assertFalse(ArrayUtilities.contains(source, (short) 9));

        Assert.assertEquals(1, ArrayUtilities.indexOf(source, (short) 2));
        Assert.assertEquals(-1, ArrayUtilities.indexOf(source, (short) 9));
        Assert.assertEquals(2, ArrayUtilities.lastIndexOf(source, (short) 3));
        Assert.assertEquals(-1, ArrayUtilities.lastIndexOf(source, (short) 9));

        Assert.assertArrayEquals(new short[] { 1 }, ArrayUtilities.append((short[]) null, (short) 1));
        Assert.assertNull(ArrayUtilities.append((short[]) null, (short[]) null));
        Assert.assertArrayEquals(new short[] { 1, 2 }, ArrayUtilities.append((short[]) null, (short) 1, (short) 2));
        Assert.assertArrayEquals(new short[] { 1, 2, 3, 4 }, ArrayUtilities.append(source, (short) 4));
        Assert.assertArrayEquals(new short[] { 1, 2, 3, 4, 5 }, ArrayUtilities.append(source, (short) 4, (short) 5));

        Assert.assertArrayEquals(new short[] { 1 }, ArrayUtilities.prepend((short[]) null, (short) 1));
        Assert.assertNull(ArrayUtilities.prepend((short[]) null, (short[]) null));
        Assert.assertArrayEquals(new short[] { 1, 2 }, ArrayUtilities.prepend((short[]) null, (short) 1, (short) 2));
        Assert.assertArrayEquals(new short[] { 9, 1, 2, 3 }, ArrayUtilities.prepend(source, (short) 9));
        Assert.assertArrayEquals(new short[] { 8, 9, 1, 2, 3 }, ArrayUtilities.prepend(source, (short) 8, (short) 9));

        Assert.assertEquals(0, ArrayUtilities.remove(new short[] { 1 }, 0).length);
        Assert.assertArrayEquals(new short[] { 1, 3 }, ArrayUtilities.remove(source, 1));

        Assert.assertArrayEquals(new short[] { 1, 9, 2, 3 }, ArrayUtilities.insert(source, 1, (short) 9));
        Assert.assertArrayEquals(new short[] { 1, 2, 3, 9 }, ArrayUtilities.insert(source, 3, (short) 9));

        final short[] insertNoValuesNull = ArrayUtilities.insert(source, 1, (short[]) null);
        Assert.assertSame(source, insertNoValuesNull);

        final short[] insertNoValuesEmpty = ArrayUtilities.insert(source, 1, new short[0]);
        Assert.assertSame(source, insertNoValuesEmpty);

        Assert.assertArrayEquals(new short[] { 1, 2, 9, 8, 3 }, ArrayUtilities.insert(source, 2, (short) 9, (short) 8));
    }

    @Test
    public void testIntPrimitiveSpecializations() {
        Assert.assertTrue(ArrayUtilities.isNullOrEmpty((int[]) null));
        Assert.assertTrue(ArrayUtilities.isNullOrEmpty(new int[0]));
        Assert.assertFalse(ArrayUtilities.isNullOrEmpty(new int[] { 1 }));

        final int[] source = new int[] { 1, 2, 3 };

        Assert.assertArrayEquals(source, ArrayUtilities.copy(source, (int[]) null));
        Assert.assertArrayEquals(new int[] { 2, 3 }, ArrayUtilities.copy(source, 1, null, 0, 2));
        Assert.assertArrayEquals(new int[] { 0, 0, 2 }, ArrayUtilities.copy(source, 1, null, 2, 1));

        final int[] shortTargetOffsetZero = new int[] { 9 };
        Assert.assertArrayEquals(new int[] { 2, 3 }, ArrayUtilities.copy(source, 1, shortTargetOffsetZero, 0, 2));

        final int[] shortTargetOffsetNonZero = new int[] { 9 };
        Assert.assertArrayEquals(new int[] { 9, 2 }, ArrayUtilities.copy(source, 1, shortTargetOffsetNonZero, 1, 1));

        final int[] largeTarget = new int[] { 8, 8, 8 };
        final int[] reused = ArrayUtilities.copy(source, 1, largeTarget, 0, 2);
        Assert.assertSame(largeTarget, reused);
        Assert.assertArrayEquals(new int[] { 2, 3, 8 }, reused);

        Assert.assertFalse(ArrayUtilities.rangeEquals(source, new int[] { 1 }, -1, 1));
        Assert.assertTrue(ArrayUtilities.rangeEquals(source, source, 0, 3));
        Assert.assertFalse(ArrayUtilities.rangeEquals(new int[] { 1, 2 }, new int[] { 1, 9 }, 0, 2));
        Assert.assertTrue(ArrayUtilities.rangeEquals(new int[] { 1, 2 }, new int[] { 1, 2 }, 0, 2));

        Assert.assertTrue(ArrayUtilities.contains(source, 2));
        Assert.assertFalse(ArrayUtilities.contains(source, 9));

        Assert.assertEquals(1, ArrayUtilities.indexOf(source, 2));
        Assert.assertEquals(-1, ArrayUtilities.indexOf(source, 9));
        Assert.assertEquals(2, ArrayUtilities.lastIndexOf(source, 3));
        Assert.assertEquals(-1, ArrayUtilities.lastIndexOf(source, 9));

        Assert.assertArrayEquals(new int[] { 1 }, ArrayUtilities.append((int[]) null, 1));
        Assert.assertNull(ArrayUtilities.append((int[]) null, (int[]) null));
        Assert.assertArrayEquals(new int[] { 1, 2 }, ArrayUtilities.append((int[]) null, 1, 2));
        Assert.assertArrayEquals(new int[] { 1, 2, 3, 4 }, ArrayUtilities.append(source, 4));
        Assert.assertArrayEquals(new int[] { 1, 2, 3, 4, 5 }, ArrayUtilities.append(source, 4, 5));

        Assert.assertArrayEquals(new int[] { 1 }, ArrayUtilities.prepend((int[]) null, 1));
        Assert.assertNull(ArrayUtilities.prepend((int[]) null, (int[]) null));
        Assert.assertArrayEquals(new int[] { 1, 2 }, ArrayUtilities.prepend((int[]) null, 1, 2));
        Assert.assertArrayEquals(new int[] { 9, 1, 2, 3 }, ArrayUtilities.prepend(source, 9));
        Assert.assertArrayEquals(new int[] { 8, 9, 1, 2, 3 }, ArrayUtilities.prepend(source, 8, 9));

        Assert.assertEquals(0, ArrayUtilities.remove(new int[] { 1 }, 0).length);
        Assert.assertArrayEquals(new int[] { 1, 3 }, ArrayUtilities.remove(source, 1));

        Assert.assertArrayEquals(new int[] { 1, 9, 2, 3 }, ArrayUtilities.insert(source, 1, 9));
        Assert.assertArrayEquals(new int[] { 1, 2, 3, 9 }, ArrayUtilities.insert(source, 3, 9));

        final int[] insertNoValuesNull = ArrayUtilities.insert(source, 1, (int[]) null);
        Assert.assertSame(source, insertNoValuesNull);

        final int[] insertNoValuesEmpty = ArrayUtilities.insert(source, 1, new int[0]);
        Assert.assertSame(source, insertNoValuesEmpty);

        Assert.assertArrayEquals(new int[] { 1, 2, 9, 8, 3 }, ArrayUtilities.insert(source, 2, 9, 8));
    }

    @Test
    public void testLongPrimitiveSpecializations() {
        Assert.assertTrue(ArrayUtilities.isNullOrEmpty((long[]) null));
        Assert.assertTrue(ArrayUtilities.isNullOrEmpty(new long[0]));
        Assert.assertFalse(ArrayUtilities.isNullOrEmpty(new long[] { 1L }));

        final long[] source = new long[] { 1L, 2L, 3L };

        Assert.assertArrayEquals(source, ArrayUtilities.copy(source, (long[]) null));
        Assert.assertArrayEquals(new long[] { 2L, 3L }, ArrayUtilities.copy(source, 1, null, 0, 2));
        Assert.assertArrayEquals(new long[] { 0L, 0L, 2L }, ArrayUtilities.copy(source, 1, null, 2, 1));

        final long[] shortTargetOffsetZero = new long[] { 9L };
        Assert.assertArrayEquals(new long[] { 2L, 3L }, ArrayUtilities.copy(source, 1, shortTargetOffsetZero, 0, 2));

        final long[] shortTargetOffsetNonZero = new long[] { 9L };
        Assert.assertArrayEquals(new long[] { 9L, 2L }, ArrayUtilities.copy(source, 1, shortTargetOffsetNonZero, 1, 1));

        final long[] largeTarget = new long[] { 8L, 8L, 8L };
        final long[] reused = ArrayUtilities.copy(source, 1, largeTarget, 0, 2);
        Assert.assertSame(largeTarget, reused);
        Assert.assertArrayEquals(new long[] { 2L, 3L, 8L }, reused);

        Assert.assertFalse(ArrayUtilities.rangeEquals(source, new long[] { 1L }, -1, 1));
        Assert.assertTrue(ArrayUtilities.rangeEquals(source, source, 0, 3));
        Assert.assertFalse(ArrayUtilities.rangeEquals(new long[] { 1L, 2L }, new long[] { 1L, 9L }, 0, 2));
        Assert.assertTrue(ArrayUtilities.rangeEquals(new long[] { 1L, 2L }, new long[] { 1L, 2L }, 0, 2));

        Assert.assertTrue(ArrayUtilities.contains(source, 2L));
        Assert.assertFalse(ArrayUtilities.contains(source, 9L));

        Assert.assertEquals(1, ArrayUtilities.indexOf(source, 2L));
        Assert.assertEquals(-1, ArrayUtilities.indexOf(source, 9L));
        Assert.assertEquals(2, ArrayUtilities.lastIndexOf(source, 3L));
        Assert.assertEquals(-1, ArrayUtilities.lastIndexOf(source, 9L));

        Assert.assertArrayEquals(new long[] { 1L }, ArrayUtilities.append((long[]) null, 1L));
        Assert.assertNull(ArrayUtilities.append((long[]) null, (long[]) null));
        Assert.assertArrayEquals(new long[] { 1L, 2L }, ArrayUtilities.append((long[]) null, 1L, 2L));
        Assert.assertArrayEquals(new long[] { 1L, 2L, 3L, 4L }, ArrayUtilities.append(source, 4L));
        Assert.assertArrayEquals(new long[] { 1L, 2L, 3L, 4L, 5L }, ArrayUtilities.append(source, 4L, 5L));

        Assert.assertArrayEquals(new long[] { 1L }, ArrayUtilities.prepend((long[]) null, 1L));
        Assert.assertNull(ArrayUtilities.prepend((long[]) null, (long[]) null));
        Assert.assertArrayEquals(new long[] { 1L, 2L }, ArrayUtilities.prepend((long[]) null, 1L, 2L));
        Assert.assertArrayEquals(new long[] { 9L, 1L, 2L, 3L }, ArrayUtilities.prepend(source, 9L));
        Assert.assertArrayEquals(new long[] { 8L, 9L, 1L, 2L, 3L }, ArrayUtilities.prepend(source, 8L, 9L));

        Assert.assertEquals(0, ArrayUtilities.remove(new long[] { 1L }, 0).length);
        Assert.assertArrayEquals(new long[] { 1L, 3L }, ArrayUtilities.remove(source, 1));

        Assert.assertArrayEquals(new long[] { 1L, 9L, 2L, 3L }, ArrayUtilities.insert(source, 1, 9L));
        Assert.assertArrayEquals(new long[] { 1L, 2L, 3L, 9L }, ArrayUtilities.insert(source, 3, 9L));

        final long[] insertNoValuesNull = ArrayUtilities.insert(source, 1, (long[]) null);
        Assert.assertSame(source, insertNoValuesNull);

        final long[] insertNoValuesEmpty = ArrayUtilities.insert(source, 1, new long[0]);
        Assert.assertSame(source, insertNoValuesEmpty);

        Assert.assertArrayEquals(new long[] { 1L, 2L, 9L, 8L, 3L }, ArrayUtilities.insert(source, 2, 9L, 8L));
    }

    @Test
    public void testFloatPrimitiveSpecializations() {
        Assert.assertTrue(ArrayUtilities.isNullOrEmpty((float[]) null));
        Assert.assertTrue(ArrayUtilities.isNullOrEmpty(new float[0]));
        Assert.assertFalse(ArrayUtilities.isNullOrEmpty(new float[] { 1.0f }));

        final float[] source = new float[] { 1.0f, 2.0f, 3.0f };

        Assert.assertArrayEquals(source, ArrayUtilities.copy(source, (float[]) null), 0.0f);
        Assert.assertArrayEquals(new float[] { 2.0f, 3.0f }, ArrayUtilities.copy(source, 1, null, 0, 2), 0.0f);
        Assert.assertArrayEquals(new float[] { 0.0f, 0.0f, 2.0f }, ArrayUtilities.copy(source, 1, null, 2, 1), 0.0f);

        final float[] shortTargetOffsetZero = new float[] { 9.0f };
        Assert.assertArrayEquals(new float[] { 2.0f, 3.0f }, ArrayUtilities.copy(source, 1, shortTargetOffsetZero, 0, 2), 0.0f);

        final float[] shortTargetOffsetNonZero = new float[] { 9.0f };
        Assert.assertArrayEquals(new float[] { 9.0f, 2.0f }, ArrayUtilities.copy(source, 1, shortTargetOffsetNonZero, 1, 1), 0.0f);

        final float[] largeTarget = new float[] { 8.0f, 8.0f, 8.0f };
        final float[] reused = ArrayUtilities.copy(source, 1, largeTarget, 0, 2);
        Assert.assertSame(largeTarget, reused);
        Assert.assertArrayEquals(new float[] { 2.0f, 3.0f, 8.0f }, reused, 0.0f);

        Assert.assertFalse(ArrayUtilities.rangeEquals(source, new float[] { 1.0f }, -1, 1));
        Assert.assertTrue(ArrayUtilities.rangeEquals(source, source, 0, 3));
        Assert.assertFalse(ArrayUtilities.rangeEquals(new float[] { 1.0f, 2.0f }, new float[] { 1.0f, 9.0f }, 0, 2));
        Assert.assertTrue(ArrayUtilities.rangeEquals(new float[] { 1.0f, 2.0f }, new float[] { 1.0f, 2.0f }, 0, 2));

        Assert.assertTrue(ArrayUtilities.contains(source, 2.0f));
        Assert.assertFalse(ArrayUtilities.contains(source, 9.0f));

        Assert.assertEquals(1, ArrayUtilities.indexOf(source, 2.0f));
        Assert.assertEquals(-1, ArrayUtilities.indexOf(source, 9.0f));
        Assert.assertEquals(2, ArrayUtilities.lastIndexOf(source, 3.0f));
        Assert.assertEquals(-1, ArrayUtilities.lastIndexOf(source, 9.0f));

        Assert.assertArrayEquals(new float[] { 1.0f }, ArrayUtilities.append((float[]) null, 1.0f), 0.0f);
        Assert.assertNull(ArrayUtilities.append((float[]) null, (float[]) null));
        Assert.assertArrayEquals(new float[] { 1.0f, 2.0f }, ArrayUtilities.append((float[]) null, 1.0f, 2.0f), 0.0f);
        Assert.assertArrayEquals(new float[] { 1.0f, 2.0f, 3.0f, 4.0f }, ArrayUtilities.append(source, 4.0f), 0.0f);
        Assert.assertArrayEquals(new float[] { 1.0f, 2.0f, 3.0f, 4.0f, 5.0f }, ArrayUtilities.append(source, 4.0f, 5.0f), 0.0f);

        Assert.assertArrayEquals(new float[] { 1.0f }, ArrayUtilities.prepend((float[]) null, 1.0f), 0.0f);
        Assert.assertNull(ArrayUtilities.prepend((float[]) null, (float[]) null));
        Assert.assertArrayEquals(new float[] { 1.0f, 2.0f }, ArrayUtilities.prepend((float[]) null, 1.0f, 2.0f), 0.0f);
        Assert.assertArrayEquals(new float[] { 9.0f, 1.0f, 2.0f, 3.0f }, ArrayUtilities.prepend(source, 9.0f), 0.0f);
        Assert.assertArrayEquals(new float[] { 8.0f, 9.0f, 1.0f, 2.0f, 3.0f }, ArrayUtilities.prepend(source, 8.0f, 9.0f), 0.0f);

        Assert.assertEquals(0, ArrayUtilities.remove(new float[] { 1.0f }, 0).length);
        Assert.assertArrayEquals(new float[] { 1.0f, 3.0f }, ArrayUtilities.remove(source, 1), 0.0f);

        Assert.assertArrayEquals(new float[] { 1.0f, 9.0f, 2.0f, 3.0f }, ArrayUtilities.insert(source, 1, 9.0f), 0.0f);
        Assert.assertArrayEquals(new float[] { 1.0f, 2.0f, 3.0f, 9.0f }, ArrayUtilities.insert(source, 3, 9.0f), 0.0f);

        final float[] insertNoValuesNull = ArrayUtilities.insert(source, 1, (float[]) null);
        Assert.assertSame(source, insertNoValuesNull);

        final float[] insertNoValuesEmpty = ArrayUtilities.insert(source, 1, new float[0]);
        Assert.assertSame(source, insertNoValuesEmpty);

        Assert.assertArrayEquals(new float[] { 1.0f, 2.0f, 9.0f, 8.0f, 3.0f }, ArrayUtilities.insert(source, 2, 9.0f, 8.0f), 0.0f);
    }

    @Test
    public void testDoublePrimitiveSpecializations() {
        Assert.assertTrue(ArrayUtilities.isNullOrEmpty((double[]) null));
        Assert.assertTrue(ArrayUtilities.isNullOrEmpty(new double[0]));
        Assert.assertFalse(ArrayUtilities.isNullOrEmpty(new double[] { 1.0d }));

        final double[] source = new double[] { 1.0d, 2.0d, 3.0d };

        Assert.assertArrayEquals(source, ArrayUtilities.copy(source, (double[]) null), 0.0d);
        Assert.assertArrayEquals(new double[] { 2.0d, 3.0d }, ArrayUtilities.copy(source, 1, null, 0, 2), 0.0d);
        Assert.assertArrayEquals(new double[] { 0.0d, 0.0d, 2.0d }, ArrayUtilities.copy(source, 1, null, 2, 1), 0.0d);

        final double[] shortTargetOffsetZero = new double[] { 9.0d };
        Assert.assertArrayEquals(new double[] { 2.0d, 3.0d }, ArrayUtilities.copy(source, 1, shortTargetOffsetZero, 0, 2), 0.0d);

        final double[] shortTargetOffsetNonZero = new double[] { 9.0d };
        Assert.assertArrayEquals(new double[] { 9.0d, 2.0d }, ArrayUtilities.copy(source, 1, shortTargetOffsetNonZero, 1, 1), 0.0d);

        final double[] largeTarget = new double[] { 8.0d, 8.0d, 8.0d };
        final double[] reused = ArrayUtilities.copy(source, 1, largeTarget, 0, 2);
        Assert.assertSame(largeTarget, reused);
        Assert.assertArrayEquals(new double[] { 2.0d, 3.0d, 8.0d }, reused, 0.0d);

        Assert.assertFalse(ArrayUtilities.rangeEquals(source, new double[] { 1.0d }, -1, 1));
        Assert.assertTrue(ArrayUtilities.rangeEquals(source, source, 0, 3));
        Assert.assertFalse(ArrayUtilities.rangeEquals(new double[] { 1.0d, 2.0d }, new double[] { 1.0d, 9.0d }, 0, 2));
        Assert.assertTrue(ArrayUtilities.rangeEquals(new double[] { 1.0d, 2.0d }, new double[] { 1.0d, 2.0d }, 0, 2));

        Assert.assertTrue(ArrayUtilities.contains(source, 2.0d));
        Assert.assertFalse(ArrayUtilities.contains(source, 9.0d));

        Assert.assertEquals(1, ArrayUtilities.indexOf(source, 2.0d));
        Assert.assertEquals(-1, ArrayUtilities.indexOf(source, 9.0d));
        Assert.assertEquals(2, ArrayUtilities.lastIndexOf(source, 3.0d));
        Assert.assertEquals(-1, ArrayUtilities.lastIndexOf(source, 9.0d));

        Assert.assertArrayEquals(new double[] { 1.0d }, ArrayUtilities.append((double[]) null, 1.0d), 0.0d);
        Assert.assertNull(ArrayUtilities.append((double[]) null, (double[]) null));
        Assert.assertArrayEquals(new double[] { 1.0d, 2.0d }, ArrayUtilities.append((double[]) null, 1.0d, 2.0d), 0.0d);
        Assert.assertArrayEquals(new double[] { 1.0d, 2.0d, 3.0d, 4.0d }, ArrayUtilities.append(source, 4.0d), 0.0d);
        Assert.assertArrayEquals(new double[] { 1.0d, 2.0d, 3.0d, 4.0d, 5.0d }, ArrayUtilities.append(source, 4.0d, 5.0d), 0.0d);

        Assert.assertArrayEquals(new double[] { 1.0d }, ArrayUtilities.prepend((double[]) null, 1.0d), 0.0d);
        Assert.assertNull(ArrayUtilities.prepend((double[]) null, (double[]) null));
        Assert.assertArrayEquals(new double[] { 1.0d, 2.0d }, ArrayUtilities.prepend((double[]) null, 1.0d, 2.0d), 0.0d);
        Assert.assertArrayEquals(new double[] { 9.0d, 1.0d, 2.0d, 3.0d }, ArrayUtilities.prepend(source, 9.0d), 0.0d);
        Assert.assertArrayEquals(new double[] { 8.0d, 9.0d, 1.0d, 2.0d, 3.0d }, ArrayUtilities.prepend(source, 8.0d, 9.0d), 0.0d);

        Assert.assertEquals(0, ArrayUtilities.remove(new double[] { 1.0d }, 0).length);
        Assert.assertArrayEquals(new double[] { 1.0d, 3.0d }, ArrayUtilities.remove(source, 1), 0.0d);

        Assert.assertArrayEquals(new double[] { 1.0d, 9.0d, 2.0d, 3.0d }, ArrayUtilities.insert(source, 1, 9.0d), 0.0d);
        Assert.assertArrayEquals(new double[] { 1.0d, 2.0d, 3.0d, 9.0d }, ArrayUtilities.insert(source, 3, 9.0d), 0.0d);

        final double[] insertNoValuesNull = ArrayUtilities.insert(source, 1, (double[]) null);
        Assert.assertSame(source, insertNoValuesNull);

        final double[] insertNoValuesEmpty = ArrayUtilities.insert(source, 1, new double[0]);
        Assert.assertSame(source, insertNoValuesEmpty);

        Assert.assertArrayEquals(new double[] { 1.0d, 2.0d, 9.0d, 8.0d, 3.0d }, ArrayUtilities.insert(source, 2, 9.0d, 8.0d), 0.0d);
    }
}

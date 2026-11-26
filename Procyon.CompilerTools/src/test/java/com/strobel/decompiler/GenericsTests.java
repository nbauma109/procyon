package com.strobel.decompiler;

import com.strobel.decompiler.fixtures.OverloadsElsewhere;
import java.io.IOException;
import java.util.stream.Stream;
import java.util.function.Supplier;
import java.util.function.Function;
import java.util.Optional;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.ArrayList;
import java.lang.reflect.Method;
import org.junit.Test;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;

@SuppressWarnings("unchecked")
public class GenericsTests extends DecompilerTest {
    private static class A {
        public static <T> void sort1(final List<T> list, final Comparator<? super T> comparator) {
            final Object[] a = list.toArray();
            Arrays.sort(a, (Comparator) comparator);
            final ListIterator i = list.listIterator();
            for (int j=0; j<a.length; j++) {
                i.next();
                i.set(a[j]);
            }
        }

        public static <T> void sort2(final List<T> list, final Comparator<? super T> comparator) {
            final Object[] a = list.toArray();
            Arrays.sort(a, (Comparator) comparator);
            final ListIterator<T> i = list.listIterator();
            for (int j=0; j<a.length; j++) {
                i.next();
                i.set((T)a[j]);
            }
        }
    }

    @Test
    public void testMixingGenericAndRawTypes() throws Throwable {
        verifyOutput(
            A.class,
            defaultSettings(),
            "private static class A {\n" +
            "    public static <T> void sort1(final List<T> list, final Comparator<? super T> comparator) {\n" +
            "        final Object[] a = list.toArray();\n" +
            "        Arrays.sort(a, (Comparator<? super Object>)comparator);\n" +
            "        final ListIterator i = list.listIterator();\n" +
            "        for (int j = 0; j < a.length; ++j) {\n" +
            "            i.next();\n" +
            "            i.set(a[j]);\n" +
            "        }\n" +
            "    }\n" +
            "    public static <T> void sort2(final List<T> list, final Comparator<? super T> comparator) {\n" +
            "        final Object[] a = list.toArray();\n" +
            "        Arrays.sort(a, (Comparator<? super Object>)comparator);\n" +
            "        final ListIterator<T> i = list.listIterator();\n" +
            "        for (int j = 0; j < a.length; ++j) {\n" +
            "            i.next();\n" +
            "            i.set((T)a[j]);\n" +
            "        }\n" +
            "    }\n" +
            "}\n"
        );

        verifyOutput(
            A.class,
            createSettings(OPTION_RETAIN_CASTS | OPTION_EXPLICIT_TYPE_ARGUMENTS),
            "private static class A {\n" +
            "    public static <T> void sort1(final List<T> list, final Comparator<? super T> comparator) {\n" +
            "        final Object[] a = list.toArray();\n" +
            "        Arrays.<Object>sort(a, (Comparator<? super Object>)comparator);\n" +
            "        final ListIterator i = list.listIterator();\n" +
            "        for (int j = 0; j < a.length; ++j) {\n" +
            "            i.next();\n" +
            "            i.set(a[j]);\n" +
            "        }\n" +
            "    }\n" +
            "    public static <T> void sort2(final List<T> list, final Comparator<? super T> comparator) {\n" +
            "        final Object[] a = list.toArray();\n" +
            "        Arrays.<Object>sort(a, (Comparator<? super Object>)comparator);\n" +
            "        final ListIterator<T> i = list.listIterator();\n" +
            "        for (int j = 0; j < a.length; ++j) {\n" +
            "            i.next();\n" +
            "            i.set((T)a[j]);\n" +
            "        }\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testNoImpreciseCastsInStreamPipelines() {
        verifyOutput(
            StreamPipelines.class,
            defaultSettings(),
            "class StreamPipelines {\n" +
            "    static class Names extends ArrayList<String> {\n" +
            "        private static final long serialVersionUID = 1L;\n" +
            "    }\n" +
            "    List<Method> methods(final Class<?> c) {\n" +
            "        return Arrays.stream(c.getMethods()).collect(Collectors.toList());\n" +
            "    }\n" +
            "    String join(final List<String> list) {\n" +
            "        return list.stream().map(String::trim).collect(Collectors.joining(\"|\"));\n" +
            "    }\n" +
            "    Map<String, Integer> lengths(final List<String> list) {\n" +
            "        return list.stream().collect(Collectors.toMap(String::trim, String::length));\n" +
            "    }\n" +
            "    long inherited(final ArrayList<String> list, final Names names) {\n" +
            "        return list.stream().map(String::trim).count() + names.stream().map(String::trim).count();\n" +
            "    }\n" +
            "}\n" +
            ""
        );
    }

    @Test
    public void testCastsOnlyWhereTheTargetDoesNotTypeTheArgument() {
        verifyOutput(
            TargetTypedArguments.class,
            defaultSettings(),
            "class TargetTypedArguments\n" +
            "{\n" +
            "    static void strings(final List<String> list) {\n" +
            "    }\n" +
            "    static void take(final Function<String, String> f) {\n" +
            "    }\n" +
            "    static void take(final Supplier<String> s) {\n" +
            "    }\n" +
            "    void genericResult() {\n" +
            "        strings(Collections.emptyList());\n" +
            "    }\n" +
            "    void overloadedWithMethodReference() {\n" +
            "        take(String::trim);\n" +
            "    }\n" +
            "    void nonGenericResult(final Optional<List<String>> o) {\n" +
            "        strings(o.orElse(Collections.emptyList()));\n" +
            "    }\n" +
            "}\n" +
            ""
        );
    }

    @Test
    public void testKeepsCastsOfMethodReferencesAdaptingTheResult() {
        verifyOutput(
            AdaptedReferences.class,
            defaultSettings(),
            "class AdaptedReferences\n" +
            "{\n" +
            "    static void print(final Object o) {\n" +
            "    }\n" +
            "    static void print(final Integer i) {\n" +
            "    }\n" +
            "    void widenedResult() {\n" +
            "        Stream.of(\"abc\").map((Function<? super String, ?>)String::length).forEach(AdaptedReferences::print);\n" +
            "    }\n" +
            "    void widenedConstruction() {\n" +
            "        Stream.of(\"abc\").map((Function<? super String, ?>)StringBuilder::new).forEach(AdaptedReferences::print);\n" +
            "    }\n" +
            "    long overloadedConstruction() {\n" +
            "        return Stream.of(\"abc\").map((Function<? super String, ?>)StringBuilder::new).count();\n" +
            "    }\n" +
            "    long castWhichCannotCompile() {\n" +
            "        return Stream.of(\"abc\").map(String::toCharArray).mapToLong(a -> a.length).sum();\n" +
            "    }\n" +
            "}\n" +
            ""
        );
    }

    @Test
    public void testNoCastToTypeVariablesOfTheInvokedMethod() {
        verifyOutput(
            ForeignTypeVariables.class,
            defaultSettings(),
            "class ForeignTypeVariables {\n" +
            "    static class Box<T> {\n" +
            "    }\n" +
            "    static <T> Box<T> boxed(final T value) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    static <T> void match(final T actual, final Box<T> expected) {\n" +
            "    }\n" +
            "    static void literal(final boolean flag) {\n" +
            "        match(Boolean.valueOf(flag), boxed(true));\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testNoExplicitTypeArgumentsForAWidenedArgument() {
        verifyOutput(
            BoundOverloads.class,
            createSettings(OPTION_EXPLICIT_TYPE_ARGUMENTS),
            "class BoundOverloads {\n" +
            "    abstract static class Both implements CharSequence, Runnable {\n" +
            "    }\n" +
            "    static class Holder<T> {\n" +
            "        T value;\n" +
            "    }\n" +
            "    <B extends Appendable> B append(final B buffer) {\n" +
            "        return buffer;\n" +
            "    }\n" +
            "    StringBuffer append(final StringBuffer buffer) {\n" +
            "        return (StringBuffer)this.append((Appendable)buffer);\n" +
            "    }\n" +
            "    <T> void accept(final T value) {\n" +
            "    }\n" +
            "    void accept(final String value) {\n" +
            "        this.accept((Object)value);\n" +
            "    }\n" +
            "    <T extends Comparable<T>> T take(final T value) {\n" +
            "        return value;\n" +
            "    }\n" +
            "    String take(final String value) {\n" +
            "        return (String)this.take((Comparable)value);\n" +
            "    }\n" +
            "    <T extends CharSequence> List<T> wrap(final T value) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    List<String> wrap(final String value) {\n" +
            "        return (List)this.wrap((CharSequence)value);\n" +
            "    }\n" +
            "    void discard(final String value) {\n" +
            "        this.wrap((CharSequence)value);\n" +
            "    }\n" +
            "    <T extends CharSequence & Runnable> T intersect(final T value) {\n" +
            "        return value;\n" +
            "    }\n" +
            "    Both intersect(final Both value) {\n" +
            "        return (Both)this.intersect((CharSequence & Runnable)value);\n" +
            "    }\n" +
            "    int length(final String value) {\n" +
            "        return this.wrap((CharSequence)value).get(0).length();\n" +
            "    }\n" +
            "    <T extends CharSequence> Holder<T> hold(final T value) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    Holder<String> hold(final String value) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    int heldLength(final String value) {\n" +
            "        return this.hold((CharSequence)value).value.length();\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testInaccessibleOverloadsDoNotRequireACast() {
        verifyOutput(
            Tosser.class,
            defaultSettings(),
            "class Tosser {\n" +
            "    void run(final IOException e) throws IOException {\n" +
            "        Thrower.toss(e);\n" +
            "    }\n" +
            "    void packageOverload(final Integer n) {\n" +
            "        Thrower.count((Number)n);\n" +
            "    }\n" +
            "    void protectedOverload(final String s) {\n" +
            "        Thrower.read((CharSequence)s);\n" +
            "    }\n" +
            "    static class Nested {\n" +
            "        void packageOverload(final Integer n) {\n" +
            "            Thrower.count((Number)n);\n" +
            "        }\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testOverloadsOfAnotherPackage() {
        verifyOutput(
            Outsider.class,
            defaultSettings(),
            "class Outsider {\n" +
            "    void run(final Integer n, final String s) {\n" +
            "        OverloadsElsewhere.count(n);\n" +
            "        OverloadsElsewhere.read(s);\n" +
            "    }\n" +
            "}\n"
        );
        verifyOutput(
            Heir.class,
            defaultSettings(),
            "class Heir extends OverloadsElsewhere {\n" +
            "    void run(final Integer n, final String s) {\n" +
            "        OverloadsElsewhere.count(n);\n" +
            "        OverloadsElsewhere.read((CharSequence)s);\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testCastToTheBoundWhichSelectsTheGenericOverload() {
        verifyOutput(
            BoundOverloads.class,
            defaultSettings(),
            "class BoundOverloads {\n" +
            "    abstract static class Both implements CharSequence, Runnable {\n" +
            "    }\n" +
            "    static class Holder<T> {\n" +
            "        T value;\n" +
            "    }\n" +
            "    <B extends Appendable> B append(final B buffer) {\n" +
            "        return buffer;\n" +
            "    }\n" +
            "    StringBuffer append(final StringBuffer buffer) {\n" +
            "        return (StringBuffer)this.append((Appendable)buffer);\n" +
            "    }\n" +
            "    <T> void accept(final T value) {\n" +
            "    }\n" +
            "    void accept(final String value) {\n" +
            "        this.accept((Object)value);\n" +
            "    }\n" +
            "    <T extends Comparable<T>> T take(final T value) {\n" +
            "        return value;\n" +
            "    }\n" +
            "    String take(final String value) {\n" +
            "        return (String)this.take((Comparable)value);\n" +
            "    }\n" +
            "    <T extends CharSequence> List<T> wrap(final T value) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    List<String> wrap(final String value) {\n" +
            "        return (List)this.wrap((CharSequence)value);\n" +
            "    }\n" +
            "    void discard(final String value) {\n" +
            "        this.wrap((CharSequence)value);\n" +
            "    }\n" +
            "    <T extends CharSequence & Runnable> T intersect(final T value) {\n" +
            "        return value;\n" +
            "    }\n" +
            "    Both intersect(final Both value) {\n" +
            "        return (Both)this.intersect((CharSequence & Runnable)value);\n" +
            "    }\n" +
            "    int length(final String value) {\n" +
            "        return this.wrap((CharSequence)value).get(0).length();\n" +
            "    }\n" +
            "    <T extends CharSequence> Holder<T> hold(final T value) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    Holder<String> hold(final String value) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    int heldLength(final String value) {\n" +
            "        return this.hold((CharSequence)value).value.length();\n" +
            "    }\n" +
            "}\n"
        );
    }
}@SuppressWarnings("unused") // decompiler fixture
class StreamPipelines {
    List<Method> methods(final Class<?> c) {
        return Arrays.stream(c.getMethods()).collect(Collectors.toList());
    }

    String join(final List<String> list) {
        return list.stream().map(String::trim).collect(Collectors.joining("|"));
    }

    Map<String, Integer> lengths(final List<String> list) {
        return list.stream().collect(Collectors.toMap(String::trim, String::length));
    }

    long inherited(final ArrayList<String> list, final Names names) {
        return list.stream().map(String::trim).count() + names.stream().map(String::trim).count();
    }

    static class Names extends ArrayList<String> {
        private static final long serialVersionUID = 1L;
    }
}

@SuppressWarnings("unused") // decompiler fixture
class TargetTypedArguments {
    static void strings(final List<String> list) {
    }

    static void take(final Function<String, String> f) {
    }

    static void take(final Supplier<String> s) {
    }

    void genericResult() {
        strings(Collections.emptyList());
    }

    void overloadedWithMethodReference() {
        take(String::trim);
    }

    void nonGenericResult(final Optional<List<String>> o) {
        strings(o.orElse(Collections.emptyList()));
    }
}

@SuppressWarnings("unused") // decompiler fixture
class AdaptedReferences {
    static void print(final Object o) {
    }

    static void print(final Integer i) {
    }

    void widenedResult() {
        Stream.of("abc").map((Function<String, Object>) String::length).forEach(AdaptedReferences::print);
    }

    void widenedConstruction() {
        Stream.of("abc").map((Function<String, Object>) StringBuilder::new).forEach(AdaptedReferences::print);
    }

    long overloadedConstruction() {
        return Stream.of("abc").map(StringBuilder::new).count();
    }

    long castWhichCannotCompile() {
        return Stream.of("abc").map(String::toCharArray).mapToLong(a -> a.length).sum();
    }
}

@SuppressWarnings("unused") // decompiler fixture
class ForeignTypeVariables {
    static class Box<T> {
    }

    static <T> Box<T> boxed(final T value) {
        return null;
    }

    static <T> void match(final T actual, final Box<T> expected) {
    }

    static void literal(final boolean flag) {
        match(flag, boxed(true));
    }
}

@SuppressWarnings("unused") // decompiler fixture
class BoundOverloads {
    <B extends Appendable> B append(final B buffer) {
        return buffer;
    }

    StringBuffer append(final StringBuffer buffer) {
        return (StringBuffer) append((Appendable) buffer);
    }

    <T> void accept(final T value) {
    }

    void accept(final String value) {
        accept((Object) value);
    }

    <T extends Comparable<T>> T take(final T value) {
        return value;
    }

    @SuppressWarnings("unchecked")
    String take(final String value) {
        return (String) take((Comparable) value);
    }

    <T extends CharSequence> List<T> wrap(final T value) {
        return null;
    }

    @SuppressWarnings("unchecked")
    List<String> wrap(final String value) {
        return (List) wrap((CharSequence) value);
    }

    void discard(final String value) {
        wrap((CharSequence) value);
    }

    <T extends CharSequence & Runnable> T intersect(final T value) {
        return value;
    }

    Both intersect(final Both value) {
        return (Both) intersect((CharSequence & Runnable) value);
    }

    int length(final String value) {
        return wrap((CharSequence) value).get(0).length();
    }

    <T extends CharSequence> Holder<T> hold(final T value) {
        return null;
    }

    Holder<String> hold(final String value) {
        return null;
    }

    int heldLength(final String value) {
        return hold((CharSequence) value).value.length();
    }

    abstract static class Both implements CharSequence, Runnable {
    }

    static class Holder<T> {
        T value;
    }
}

@SuppressWarnings("unused") // decompiler fixture
class Thrower {
    static <E extends Exception> void toss(final E e) throws E {
        throw e;
    }

    private static void toss(final IOException e) {
    }

    static <N extends Number> void count(final N n) {
    }

    static void count(final Integer n) {
    }

    protected static <C extends CharSequence> void read(final C c) {
    }

    protected static void read(final String s) {
    }
}

@SuppressWarnings("unused") // decompiler fixture
class Tosser {
    void run(final IOException e) throws IOException {
        Thrower.toss(e);
    }

    void packageOverload(final Integer n) {
        Thrower.count((Number) n);
    }

    void protectedOverload(final String s) {
        Thrower.read((CharSequence) s);
    }

    static class Nested {
        void packageOverload(final Integer n) {
            Thrower.count((Number) n);
        }
    }
}

@SuppressWarnings("unused") // decompiler fixture
class Outsider {
    void run(final Integer n, final String s) {
        OverloadsElsewhere.count(n);
        OverloadsElsewhere.read(s);
    }
}

@SuppressWarnings("unused") // decompiler fixture
class Heir extends OverloadsElsewhere {
    void run(final Integer n, final String s) {
        OverloadsElsewhere.count(n);
        OverloadsElsewhere.read((CharSequence) s);
    }
}

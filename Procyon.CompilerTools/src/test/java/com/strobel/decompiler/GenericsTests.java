package com.strobel.decompiler;

import com.strobel.decompiler.fixtures.OverloadsElsewhere;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.stream.Stream;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.Optional;
import java.util.Collections;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.ArrayList;
import java.util.Iterator;
import java.lang.reflect.Method;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;

@SuppressWarnings("unchecked")
public class GenericsTests extends DecompilerTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testLocalsOfWidenedResultsWithoutDebugInformation() throws IOException {
        final File source = folder.newFile("Widened.java");

        Files.write(
            source.toPath(),
            Arrays.asList(
                "public class Widened {",
                "    <T extends CharSequence> T take(T t) { return t; }",
                "    String take(String s) { return s; }",
                "    <T extends CharSequence> T[] array(T t) { return null; }",
                "    String[] array(String s) { return null; }",
                "    int single(String s) {",
                "        CharSequence c = take((CharSequence) s);",
                "        return c.length() + c.charAt(0);",
                "    }",
                "    int many(String s) {",
                "        CharSequence[] c = array((CharSequence) s);",
                "        return c.length + c[0].length();",
                "    }",
                "    static class Box<X> { X get() { return null; } }",
                "    static class Child extends Box<String> { }",
                "    <T extends Box<?>> T pick(T t) { return t; }",
                "    Child pick(Child c) { return c; }",
                "    <T extends CharSequence & java.io.Serializable> T both(T t) { return t; }",
                "    String both(String s) { return s; }",
                "    int intersected(String s) {",
                "        CharSequence c = both((CharSequence & java.io.Serializable) s);",
                "        return c.length() + c.charAt(0);",
                "    }",
                "    static class Keyed<X> { X get(String key) { return null; } }",
                "    static class Fixed extends Keyed<String> { }",
                "    <T extends Keyed<?>> T keyed(T t) { return t; }",
                "    Fixed keyed(Fixed f) { return f; }",
                "    java.util.function.Function<String, String> applied(Fixed f) {",
                "        Keyed<String> k = (Keyed<String>) (Keyed) keyed((Keyed<?>) f);",
                "        return k::get;",
                "    }",
                "    static class Many<X> { X[] all() { return null; } }",
                "    static class Several extends Many<String> { }",
                "    <T extends Many<?>> T many(T t) { return t; }",
                "    Several many(Several s) { return s; }",
                "    interface Fn<A, R extends Number> { R apply(A a); }",
                "    static class Counted<X extends Number> { X get(String key) { return null; } }",
                "    static class Count extends Counted<Integer> { }",
                "    <T extends Counted<?>> T counted(T t) { return t; }",
                "    Count counted(Count c) { return c; }",
                "    Fn<String, Integer> numbers(Count c) {",
                "        Counted<Integer> n = (Counted<Integer>) (Counted) counted((Counted<?>) c);",
                "        return n::get;",
                "    }",
                "    java.util.function.UnaryOperator<String> unary(Fixed f) {",
                "        Keyed<String> u = (Keyed<String>) (Keyed) keyed((Keyed<?>) f);",
                "        return u::get;",
                "    }",
                "    java.util.function.IntSupplier supply(Whole c) {",
                "        Counted2<Integer> w = (Counted2<Integer>) (Counted2) whole((Counted2<?>) c);",
                "        return w::get;",
                "    }",
                "    <T extends Counted2<?>> T whole(T t) { return t; }",
                "    Whole whole(Whole w) { return w; }",
                "    static class Counted2<X> { X get() { return null; } }",
                "    static class Whole extends Counted2<Integer> { }",
                "    static class Acting<X extends Exception> { void act() throws X { } }",
                "    static class Acts extends Acting<java.io.IOException> { }",
                "    <T extends Acting<?>> T acting(T t) { return t; }",
                "    Acts acting(Acts a) { return a; }",
                "    interface Action { void run() throws java.io.IOException; }",
                "    Action deferred(Acts a) {",
                "        Acting<java.io.IOException> d = (Acting<java.io.IOException>) (Acting) acting((Acting<?>) a);",
                "        return d::act;",
                "    }",
                "    int chosen(boolean flag, String s) {",
                "        CharSequence c = flag ? take((CharSequence) s) : s;",
                "        return c.length() + c.charAt(0);",
                "    }",
                "    interface Throwing<E extends Exception> { void run() throws E; }",
                "    Throwing<java.io.IOException> postponed(Acts a) {",
                "        Acting<java.io.IOException> g = (Acting<java.io.IOException>) (Acting) acting((Acting<?>) a);",
                "        return g::act;",
                "    }",
                "    static class Both2<X, Y> { X get() { return null; } }",
                "    static class Pairing extends Both2<Integer, String> { }",
                "    <T extends Both2<?, ?>> T both2(T t) { return t; }",
                "    Pairing both2(Pairing p) { return p; }",
                "    java.util.function.IntSupplier paired(Pairing p) {",
                "        Both2<Integer, String> two = (Both2<Integer, String>) (Both2) both2((Both2<?, ?>) p);",
                "        return two::get;",
                "    }",
                "    static class Mapping<X, Y> { X get(Y y) { return null; } }",
                "    static class Mapped extends Mapping<Integer, Integer> { }",
                "    <T extends Mapping<?, ?>> T mapping(T t) { return t; }",
                "    Mapped mapping(Mapped m) { return m; }",
                "    java.util.function.IntUnaryOperator mappedBy(Mapped m) {",
                "        Mapping<Integer, Integer> o = (Mapping<Integer, Integer>) (Mapping) mapping((Mapping<?, ?>) m);",
                "        return o::get;",
                "    }",
                "    static class Giving<X, E extends Exception> { X get() throws E { return null; } }",
                "    static class Gives extends Giving<String, java.io.IOException> { }",
                "    <T extends Giving<?, ?>> T giving(T t) { return t; }",
                "    Gives giving(Gives g) { return g; }",
                "    interface Source { String run() throws java.io.IOException; }",
                "    Source sourced(Gives a) {",
                "        Giving<String, java.io.IOException> v = (Giving<String, java.io.IOException>) (Giving) giving((Giving<?, ?>) a);",
                "        return v::get;",
                "    }",
                "    interface Strings { String[] get(); }",
                "    Strings fixed(Several s) {",
                "        Many<String> f = (Many<String>) (Many) many((Many<?>) s);",
                "        return f::all;",
                "    }",
                "    java.util.function.Supplier<String[]> arrays(Several s) {",
                "        Many<String> m = (Many<String>) (Many) many((Many<?>) s);",
                "        return m::all;",
                "    }",
                "    java.util.function.Supplier<String> supplied(Child c) {",
                "        Box<String> b = (Box<String>) (Box) pick((Box<?>) c);",
                "        return b::get;",
                "    }",
                "    String looped(Child c, int n) {",
                "        Box<?> b = c;",
                "        String s = \"\";",
                "        while (n-- > 0) {",
                "            s += ((String) b.get()).substring(1);",
                "            b = pick((Box<?>) c);",
                "        }",
                "        return s;",
                "    }",
                "    String member(Child c) {",
                "        Box<?> b = pick((Box<?>) c);",
                "        return ((String) b.get()).substring(1) + b.hashCode();",
                "    }",
                "    int element(String s) {",
                "        CharSequence c = array((CharSequence) s)[0];",
                "        return c.length() + c.charAt(0);",
                "    }",
                "}"
            ),
            StandardCharsets.UTF_8
        );

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        assertEquals(0, compiler.run(null, null, null, "-g:none", "-d", folder.getRoot().getPath(), source.getPath()));

        verifyOutput(
            new File(folder.getRoot(), "Widened.class").getPath(),
            defaultSettings(),
            "public class Widened {\n" +
            "    <T extends CharSequence> T take(final T t) {\n" +
            "        return t;\n" +
            "    }\n" +
            "    String take(final String s) {\n" +
            "        return s;\n" +
            "    }\n" +
            "    <T extends CharSequence> T[] array(final T t) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    String[] array(final String s) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    int single(final String s) {\n" +
            "        final CharSequence take = this.take((CharSequence)s);\n" +
            "        return take.length() + take.charAt(0);\n" +
            "    }\n" +
            "    int many(final String s) {\n" +
            "        final CharSequence[] array = this.array((CharSequence)s);\n" +
            "        return array.length + array[0].length();\n" +
            "    }\n" +
            "    <T extends Box<?>> T pick(final T t) {\n" +
            "        return t;\n" +
            "    }\n" +
            "    Child pick(final Child child) {\n" +
            "        return child;\n" +
            "    }\n" +
            "    <T extends CharSequence & Serializable> T both(final T t) {\n" +
            "        return t;\n" +
            "    }\n" +
            "    String both(final String s) {\n" +
            "        return s;\n" +
            "    }\n" +
            "    int intersected(final String s) {\n" +
            "        final CharSequence both = this.both((CharSequence & Serializable)s);\n" +
            "        return both.length() + both.charAt(0);\n" +
            "    }\n" +
            "    <T extends Keyed<?>> T keyed(final T t) {\n" +
            "        return t;\n" +
            "    }\n" +
            "    Fixed keyed(final Fixed fixed) {\n" +
            "        return fixed;\n" +
            "    }\n" +
            "    Function<String, String> applied(final Fixed fixed) {\n" +
            "        return (Function)(Function<String, Object>)this.keyed((Keyed)fixed)::get;\n" +
            "    }\n" +
            "    <T extends Many<?>> T many(final T t) {\n" +
            "        return t;\n" +
            "    }\n" +
            "    Several many(final Several several) {\n" +
            "        return several;\n" +
            "    }\n" +
            "    <T extends Counted<?>> T counted(final T t) {\n" +
            "        return t;\n" +
            "    }\n" +
            "    Count counted(final Count count) {\n" +
            "        return count;\n" +
            "    }\n" +
            "    Fn<String, Integer> numbers(final Count count) {\n" +
            "        return (Fn)(Fn<String, Number>)this.counted((Counted)count)::get;\n" +
            "    }\n" +
            "    UnaryOperator<String> unary(final Fixed fixed) {\n" +
            "        return ((Keyed<String>)(Keyed)this.keyed((Keyed)fixed))::get;\n" +
            "    }\n" +
            "    IntSupplier supply(final Whole whole) {\n" +
            "        return ((Counted2<Integer>)(Counted2)this.whole((Counted2)whole))::get;\n" +
            "    }\n" +
            "    <T extends Counted2<?>> T whole(final T t) {\n" +
            "        return t;\n" +
            "    }\n" +
            "    Whole whole(final Whole whole) {\n" +
            "        return whole;\n" +
            "    }\n" +
            "    <T extends Acting<?>> T acting(final T t) {\n" +
            "        return t;\n" +
            "    }\n" +
            "    Acts acting(final Acts acts) {\n" +
            "        return acts;\n" +
            "    }\n" +
            "    Action deferred(final Acts acts) {\n" +
            "        return ((Acting<IOException>)(Acting)this.acting((Acting)acts))::act;\n" +
            "    }\n" +
            "    int chosen(final boolean b, final String s) {\n" +
            "        final CharSequence s2 = b ? this.take((CharSequence)s) : s;\n" +
            "        return s2.length() + s2.charAt(0);\n" +
            "    }\n" +
            "    Throwing<IOException> postponed(final Acts acts) {\n" +
            "        return ((Acting<IOException>)(Acting)this.acting((Acting)acts))::act;\n" +
            "    }\n" +
            "    <T extends Both2<?, ?>> T both2(final T t) {\n" +
            "        return t;\n" +
            "    }\n" +
            "    Pairing both2(final Pairing pairing) {\n" +
            "        return pairing;\n" +
            "    }\n" +
            "    IntSupplier paired(final Pairing pairing) {\n" +
            "        return ((Both2<Integer, ?>)(Both2)this.both2((Both2)pairing))::get;\n" +
            "    }\n" +
            "    <T extends Mapping<?, ?>> T mapping(final T t) {\n" +
            "        return t;\n" +
            "    }\n" +
            "    Mapped mapping(final Mapped mapped) {\n" +
            "        return mapped;\n" +
            "    }\n" +
            "    IntUnaryOperator mappedBy(final Mapped mapped) {\n" +
            "        return ((Mapping<Integer, Integer>)(Mapping)this.mapping((Mapping)mapped))::get;\n" +
            "    }\n" +
            "    <T extends Giving<?, ?>> T giving(final T t) {\n" +
            "        return t;\n" +
            "    }\n" +
            "    Gives giving(final Gives gives) {\n" +
            "        return gives;\n" +
            "    }\n" +
            "    Source sourced(final Gives gives) {\n" +
            "        return ((Giving<String, IOException>)(Giving)this.giving((Giving)gives))::get;\n" +
            "    }\n" +
            "    Strings fixed(final Several several) {\n" +
            "        return ((Many<String>)(Many)this.many((Many)several))::all;\n" +
            "    }\n" +
            "    Supplier<String[]> arrays(final Several several) {\n" +
            "        return (Supplier)this.many((Many)several)::all;\n" +
            "    }\n" +
            "    Supplier<String> supplied(final Child child) {\n" +
            "        return (Supplier)this.pick((Box)child)::get;\n" +
            "    }\n" +
            "    String looped(final Child child, int n) {\n" +
            "        Box pick = child;\n" +
            "        String string = \"\";\n" +
            "        while (n-- > 0) {\n" +
            "            string += ((String)pick.get()).substring(1);\n" +
            "            pick = this.pick((Box)child);\n" +
            "        }\n" +
            "        return string;\n" +
            "    }\n" +
            "    String member(final Child child) {\n" +
            "        final Box pick = this.pick((Box)child);\n" +
            "        return ((String)pick.get()).substring(1) + pick.hashCode();\n" +
            "    }\n" +
            "    int element(final String s) {\n" +
            "        final CharSequence s2 = this.array((CharSequence)s)[0];\n" +
            "        return s2.length() + s2.charAt(0);\n" +
            "    }\n" +
            "    interface Strings {\n" +
            "        String[] get();\n" +
            "    }\n" +
            "    interface Source {\n" +
            "        String run() throws IOException;\n" +
            "    }\n" +
            "    static class Gives extends Giving<String, IOException> {\n" +
            "    }\n" +
            "    static class Giving<X, E extends Exception> {\n" +
            "        X get() throws E, Exception {\n" +
            "            return null;\n" +
            "        }\n" +
            "    }\n" +
            "    static class Mapped extends Mapping<Integer, Integer> {\n" +
            "    }\n" +
            "    static class Mapping<X, Y> {\n" +
            "        X get(final Y y) {\n" +
            "            return null;\n" +
            "        }\n" +
            "    }\n" +
            "    static class Pairing extends Both2<Integer, String> {\n" +
            "    }\n" +
            "    static class Both2<X, Y> {\n" +
            "        X get() {\n" +
            "            return null;\n" +
            "        }\n" +
            "    }\n" +
            "    interface Throwing<E extends Exception> {\n" +
            "        void run() throws E, Exception;\n" +
            "    }\n" +
            "    interface Action {\n" +
            "        void run() throws IOException;\n" +
            "    }\n" +
            "    static class Acts extends Acting<IOException> {\n" +
            "    }\n" +
            "    static class Acting<X extends Exception> {\n" +
            "        void act() throws X, Exception {\n" +
            "        }\n" +
            "    }\n" +
            "    static class Whole extends Counted2<Integer> {\n" +
            "    }\n" +
            "    static class Counted2<X> {\n" +
            "        X get() {\n" +
            "            return null;\n" +
            "        }\n" +
            "    }\n" +
            "    static class Count extends Counted<Integer> {\n" +
            "    }\n" +
            "    static class Counted<X extends Number> {\n" +
            "        X get(final String s) {\n" +
            "            return null;\n" +
            "        }\n" +
            "    }\n" +
            "    interface Fn<A, R extends Number> {\n" +
            "        R apply(final A p0);\n" +
            "    }\n" +
            "    static class Several extends Many<String> {\n" +
            "    }\n" +
            "    static class Many<X> {\n" +
            "        X[] all() {\n" +
            "            return null;\n" +
            "        }\n" +
            "    }\n" +
            "    static class Fixed extends Keyed<String> {\n" +
            "    }\n" +
            "    static class Keyed<X> {\n" +
            "        X get(final String s) {\n" +
            "            return null;\n" +
            "        }\n" +
            "    }\n" +
            "    static class Child extends Box<String> {\n" +
            "    }\n" +
            "    static class Box<X> {\n" +
            "        X get() {\n" +
            "            return null;\n" +
            "        }\n" +
            "    }\n" +
            "}\n"
        );
    }

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
    public void testExplicitTypeArgumentsForAWidenedArgument() {
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
            "        return (StringBuffer)this.<Appendable>append((Appendable)buffer);\n" +
            "    }\n" +
            "    <T> void accept(final T value) {\n" +
            "    }\n" +
            "    void accept(final String value) {\n" +
            "        this.<Object>accept((Object)value);\n" +
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
            "        return (List)this.<CharSequence>wrap((CharSequence)value);\n" +
            "    }\n" +
            "    void discard(final String value) {\n" +
            "        this.<CharSequence>wrap((CharSequence)value);\n" +
            "    }\n" +
            "    <T extends CharSequence & Runnable> T intersect(final T value) {\n" +
            "        return value;\n" +
            "    }\n" +
            "    Both intersect(final Both value) {\n" +
            "        return (Both)this.intersect((CharSequence & Runnable)value);\n" +
            "    }\n" +
            "    int length(final String value) {\n" +
            "        return this.<CharSequence>wrap((CharSequence)value).get(0).length();\n" +
            "    }\n" +
            "    <T extends CharSequence> Holder<T> hold(final T value) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    Holder<String> hold(final String value) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    int heldLength(final String value) {\n" +
            "        return this.<CharSequence>hold((CharSequence)value).value.length();\n" +
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
    public void testBoundWhichIsAClassTypeVariable() {
        verifyOutput(
            ClassBound.class,
            defaultSettings(),
            "class ClassBound<X> {\n" +
            "    <T extends X> T first(final T value) {\n" +
            "        return value;\n" +
            "    }\n" +
            "    String first(final String value) {\n" +
            "        return (String)this.first((X)value);\n" +
            "    }\n" +
            "    <U extends X, V extends U> V second(final V value) {\n" +
            "        return value;\n" +
            "    }\n" +
            "    String second(final String value) {\n" +
            "        return (String)this.second((X)value);\n" +
            "    }\n" +
            "    <T extends CharSequence> List<T>[] array(final T value) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    List<String>[] array(final String value) {\n" +
            "        return (List[])this.array((CharSequence)value);\n" +
            "    }\n" +
            "    String other(final ClassBound<CharSequence> other, final String value) {\n" +
            "        return (String)other.first((CharSequence)value);\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testBoundCastDoesNotWidenTheThrownType() {
        verifyOutput(
            FunnelCaller.class,
            defaultSettings(),
            "class FunnelCaller {\n" +
            "    void run(final FileNotFoundException e) throws IOException {\n" +
            "        Funnel.toss((IOException)e);\n" +
            "    }\n" +
            "    void relayed(final FileNotFoundException e) throws IOException {\n" +
            "        Funnel.relay((IOException)e);\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testBoundWhichIsAVariableOfTheCaller() {
        verifyOutput(
            ShelfCaller.class,
            defaultSettings(),
            "class ShelfCaller {\n" +
            "    <Y> String borrow(final Shelf<Y> shelf, final String s) {\n" +
            "        return (String)shelf.take((Y)s);\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testIndependentTypeArgumentsAndElementsOfWidenedCalls() {
        verifyOutput(
            Pairs.class,
            defaultSettings(),
            "class Pairs {\n" +
            "    <T extends CharSequence, R> List<R> both(final T t) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    List<String> both(final String s) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    <T extends CharSequence> List<T>[] arrays(final T t) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    List<String>[] arrays(final String s) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    int independent(final String s) {\n" +
            "        return ((String)this.<CharSequence, String>both((CharSequence)s).get(0)).length();\n" +
            "    }\n" +
            "    int element(final String s) {\n" +
            "        final List<CharSequence> list = (List)this.arrays((CharSequence)s)[0];\n" +
            "        return list.size();\n" +
            "    }\n" +
            "    <T extends CharSequence> Crate<T> crate(final T t) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    Crate<String> crate(final String s) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    int field(final String s) {\n" +
            "        final List<CharSequence> values = (List)this.crate((CharSequence)s).values;\n" +
            "        return values.size();\n" +
            "    }\n" +
            "    int fieldElement(final String s) {\n" +
            "        final List<CharSequence> list = (List)this.crate((CharSequence)s).shelves[0];\n" +
            "        return list.size();\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testDependentArgumentsAndLowerBoundedReceivers() {
        verifyOutput(
            Partners.class,
            defaultSettings(),
            "class Partners {\n" +
            "    <T extends CharSequence> void pair(final T t, final List<T> list) {\n" +
            "    }\n" +
            "    void pair(final String s, final List<String> list) {\n" +
            "    }\n" +
            "    void dependent(final String s, final List<String> list) {\n" +
            "        this.pair((CharSequence)s, (List)list);\n" +
            "    }\n" +
            "    String lower(final Shelf<? super CharSequence> shelf, final String s) {\n" +
            "        return (String)shelf.take((CharSequence)s);\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testNarrowingCastsOnMembersOfWidenedResults() {
        verifyOutput(
            Narrowed.class,
            defaultSettings(),
            "class Narrowed {\n" +
            "    <T extends CharSequence> Crate<T> crate(final T t) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    Crate<String> crate(final String s) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    String element(final String s) {\n" +
            "        return ((String)this.crate((CharSequence)s).values.get(0)).substring(1);\n" +
            "    }\n" +
            "    String field(final String s) {\n" +
            "        return ((String)this.crate((CharSequence)s).value).substring(1);\n" +
            "    }\n" +
            "    int wide(final String s) {\n" +
            "        return this.crate((CharSequence)s).values.get(0).length();\n" +
            "    }\n" +
            "    <T extends CharSequence> void pair(final T t, final List<T>[] lists) {\n" +
            "    }\n" +
            "    void pair(final String s, final List<String>[] lists) {\n" +
            "    }\n" +
            "    void arrays(final String s, final List<String>[] lists) {\n" +
            "        this.pair((CharSequence)s, (List[])lists);\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testDependentFunctionsAndApplicableOverloads() {
        verifyOutput(
            Consumers.class,
            defaultSettings(),
            "class Consumers {\n" +
            "    <T extends CharSequence> void take(final T t, final Consumer<T> consumer) {\n" +
            "    }\n" +
            "    void take(final String s, final Consumer<String> consumer) {\n" +
            "    }\n" +
            "    static void print(final String value) {\n" +
            "    }\n" +
            "    void lambda(final String s) {\n" +
            "        this.take((CharSequence)s, (Consumer)(Consumer<String>)(x -> print(x.substring(1))));\n" +
            "    }\n" +
            "    void reference(final String s) {\n" +
            "        this.take((CharSequence)s, (Consumer)(Consumer<String>)Consumers::print);\n" +
            "    }\n" +
            "    <T extends CharSequence> void count(final T t, final int n) {\n" +
            "    }\n" +
            "    void count(final String s, final String n) {\n" +
            "    }\n" +
            "    void inapplicable(final String s) {\n" +
            "        this.count(s, 1);\n" +
            "    }\n" +
            "    <T extends CharSequence> Crate<T> crate(final T t) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    Crate<String> crate(final String s) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    String array(final String s) {\n" +
            "        return ((String[])(String[])this.crate((CharSequence)s).array())[0].substring(1);\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testIndexedNarrowingAndPrimitiveOverloads() {
        verifyOutput(
            Indexed.class,
            defaultSettings(),
            "class Indexed {\n" +
            "    <T extends CharSequence> Crate<T> crate(final T t) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    Crate<String> crate(final String s) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    String element(final String s) {\n" +
            "        return ((String)this.crate((CharSequence)s).items[0]).substring(1);\n" +
            "    }\n" +
            "    <N extends Number> void flag(final N n, final int i) {\n" +
            "    }\n" +
            "    void flag(final Integer n, final boolean b) {\n" +
            "    }\n" +
            "    <N extends Number> void widen(final N n, final int i) {\n" +
            "    }\n" +
            "    void widen(final Integer n, final long i) {\n" +
            "    }\n" +
            "    <N extends Number> void box(final N n, final int i) {\n" +
            "    }\n" +
            "    void box(final Integer n, final Object o) {\n" +
            "    }\n" +
            "    void primitives(final Integer n) {\n" +
            "        this.flag(n, 1);\n" +
            "        this.widen((Number)n, 1);\n" +
            "        this.box(n, 1);\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testConsumedChainsAndOverloadsWhichCannotDisplace() {
        verifyOutput(
            Chained.class,
            defaultSettings(),
            "class Chained {\n" +
            "    static List<String> shared;\n" +
            "    List<String> field;\n" +
            "    <T extends CharSequence> List<T> wrap(final T t) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    List<String> wrap(final String s) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    static void use(final List<String> list) {\n" +
            "    }\n" +
            "    List<String> returned(final String s) {\n" +
            "        return (List)this.wrap((CharSequence)s).subList(0, 1);\n" +
            "    }\n" +
            "    void passed(final String s) {\n" +
            "        use((List)this.wrap((CharSequence)s).subList(0, 1));\n" +
            "    }\n" +
            "    void stored(final String s, final List<String>[] lists) {\n" +
            "        this.field = (List)this.wrap((CharSequence)s).subList(0, 1);\n" +
            "        Chained.shared = (List)this.wrap((CharSequence)s).subList(0, 1);\n" +
            "        lists[0] = (List)this.wrap((CharSequence)s).subList(0, 1);\n" +
            "    }\n" +
            "    <N extends Number> void constrained(final N n, final List<Integer> list) {\n" +
            "    }\n" +
            "    void constrained(final Integer n, final Collection<String> list) {\n" +
            "    }\n" +
            "    <N extends Integer> void specific(final N n) {\n" +
            "    }\n" +
            "    void specific(final Number n) {\n" +
            "    }\n" +
            "    void direct(final Integer n, final List<Integer> list) {\n" +
            "        this.constrained(n, list);\n" +
            "        this.specific(n);\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testProtectedOverloadsThroughAReceiver() {
        verifyOutput(
            RestrictedHeir.class,
            defaultSettings(),
            "class RestrictedHeir extends OverloadsElsewhere {\n" +
            "    void through(final OverloadsElsewhere other, final String s) {\n" +
            "        other.send(s);\n" +
            "        this.send((CharSequence)s);\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testDirectArraysAndRestrictedOverloads() {
        verifyOutput(
            Restricted.class,
            defaultSettings(),
            "class Restricted {\n" +
            "    <T extends CharSequence> T[] array(final T t) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    String[] array(final String s) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    String element(final String s) {\n" +
            "        return ((String)this.array((CharSequence)s)[0]).substring(1);\n" +
            "    }\n" +
            "    <N extends Number> void arrays(final N n, final List<Integer>[] lists) {\n" +
            "    }\n" +
            "    void arrays(final Integer n, final Collection<String>[] lists) {\n" +
            "    }\n" +
            "    void protectedElsewhere(final OverloadsElsewhere other, final Integer n, final String s) {\n" +
            "        OverloadsElsewhere.count(n);\n" +
            "        other.send(s);\n" +
            "    }\n" +
            "    void direct(final Integer n, final List<Integer>[] lists) {\n" +
            "        this.arrays(n, lists);\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testWildcardsScalarResultsConditionalsAndCoordinatedCasts() {
        verifyOutput(
            Coordinated.class,
            defaultSettings(),
            "class Coordinated {\n" +
            "    <T extends Coffer<?>> T take(final T t) {\n" +
            "        return t;\n" +
            "    }\n" +
            "    Casket take(final Casket casket) {\n" +
            "        return casket;\n" +
            "    }\n" +
            "    String scalar(final Casket casket) {\n" +
            "        return ((String)this.take((Coffer)casket).get()).substring(1);\n" +
            "    }\n" +
            "    <T extends CharSequence> List<T> wrap(final T t) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    List<String> wrap(final String s) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    List<String> conditional(final boolean flag, final String s, final List<String> other) {\n" +
            "        return flag ? ((List)this.wrap((CharSequence)s).subList(0, 1)) : other;\n" +
            "    }\n" +
            "    <N extends Number> void bounded(final N n, final List<? extends Number> list) {\n" +
            "    }\n" +
            "    void bounded(final Integer n, final Collection<Integer> list) {\n" +
            "    }\n" +
            "    <E extends Exception, R> void toss(final E e, final R r) throws E, Exception {\n" +
            "    }\n" +
            "    void toss(final IOException e, final String s) {\n" +
            "    }\n" +
            "    void direct(final Integer n, final List<? extends Number> list, final IOException e, final String s) throws IOException {\n" +
            "        this.bounded(n, list);\n" +
            "        this.toss(e, (Object)s);\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testBoundedCandidatesAndInaccessibleGenericSupertypes() {
        verifyOutput(
            Shadows.class,
            defaultSettings(),
            "class Shadows {\n" +
            "    <E extends Number> void toss(final E e, final String s) {\n" +
            "    }\n" +
            "    <N extends Number> void toss(final Integer e, final N n) {\n" +
            "    }\n" +
            "    List<String> shaded(final String s) {\n" +
            "        return (ArrayList)OverloadsElsewhere.shaded((CharSequence)s);\n" +
            "    }\n" +
            "    void direct(final Integer n) {\n" +
            "        this.toss(n, \"x\");\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testParameterizedBoundsRawArgumentsAndConditionalReceivers() {
        verifyOutput(
            Forked.class,
            defaultSettings(),
            "class Forked {\n" +
            "    <E extends Number> void toss(final E e, final List<Integer> list) {\n" +
            "    }\n" +
            "    <N extends Collection<String>> void toss(final Integer e, final N n) {\n" +
            "    }\n" +
            "    <T extends CharSequence, R> List<R> wrap(final T t, final Map<T, R> map) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    List<CharSequence> wrap(final String s, final Map<String, CharSequence> map) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    <T extends CharSequence> List<T> single(final T t) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    List<String> single(final String s) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    void bounded(final Integer n, final List<Integer> list) {\n" +
            "        this.toss(n, list);\n" +
            "    }\n" +
            "    int raw(final String s, final Map<String, CharSequence> map) {\n" +
            "        return ((CharSequence)this.wrap((CharSequence)s, (Map)map).get(0)).length();\n" +
            "    }\n" +
            "    String chosen(final boolean flag, final String s, final List<String> other) {\n" +
            "        return ((String)(flag ? this.single((CharSequence)s) : other).get(0)).substring(1);\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testPartiallyGenericCandidatesAndUninferredResults() {
        verifyOutput(
            Partial.class,
            defaultSettings(),
            "class Partial {\n" +
            "    <E extends Number> void toss(final E e, final Map<Integer, Integer> map) {\n" +
            "    }\n" +
            "    <N> void toss(final Integer e, final Map<String, N> map) {\n" +
            "    }\n" +
            "    <T extends CharSequence & Serializable, R> List<R> wrap(final T t) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    List<String> wrap(final String s) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    void partly(final Integer n, final Map<Integer, Integer> map) {\n" +
            "        this.toss(n, map);\n" +
            "    }\n" +
            "    int uninferred(final String s) {\n" +
            "        return ((CharSequence)this.wrap((CharSequence & Serializable)s).get(0)).length();\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testNestedBoundsAndIncompatibleLoopItems() {
        verifyOutput(
            Nested2.class,
            defaultSettings(),
            "class Nested2 {\n" +
            "    <E extends Number> void toss(final E e, final List<String> list) {\n" +
            "    }\n" +
            "    <N extends Number> void toss(final Integer e, final Collection<N> list) {\n" +
            "    }\n" +
            "    <T extends CharSequence> List<T> take(final T t) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    List<String> take(final String s) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    void nested(final Integer n, final List<String> list) {\n" +
            "        this.toss(n, list);\n" +
            "    }\n" +
            "    void items(final String s) {\n" +
            "        final Iterator<String> iterator = (Iterator)this.take((CharSequence)s).iterator();\n" +
            "        while (iterator.hasNext()) {\n" +
            "            final Serializable item = (Serializable)iterator.next();\n" +
            "            System.out.println(item.hashCode());\n" +
            "        }\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testSharedVariablesSeveralBoundsAndCompatibleArguments() {
        verifyOutput(
            Shared.class,
            defaultSettings(),
            "class Shared {\n" +
            "    <E extends Number> void toss(final E e, final List<String> a, final List<Integer> b) {\n" +
            "    }\n" +
            "    <N> void toss(final Integer e, final Collection<N> a, final Collection<N> b) {\n" +
            "    }\n" +
            "    <E extends Number> void deep(final E e, final List<List<String>> lists) {\n" +
            "    }\n" +
            "    <N extends Number> void deep(final Integer e, final Collection<List<N>> lists) {\n" +
            "    }\n" +
            "    void nested(final Integer e, final List<List<String>> lists) {\n" +
            "        this.deep(e, lists);\n" +
            "    }\n" +
            "    <E extends Number> void apart(final E e, final List<List<String>> a, final List<List<Integer>> b) {\n" +
            "    }\n" +
            "    <N> void apart(final Integer e, final Collection<List<N>> a, final Collection<List<N>> b) {\n" +
            "    }\n" +
            "    void separate(final Integer e, final List<List<String>> a, final List<List<Integer>> b) {\n" +
            "        this.apart(e, a, b);\n" +
            "    }\n" +
            "    <E extends Number> void under(final E e, final List<String> list) {\n" +
            "    }\n" +
            "    <N extends Number> void under(final Integer e, final Collection<? super N> list) {\n" +
            "    }\n" +
            "    void below(final Integer e, final List<String> list) {\n" +
            "        this.under(e, list);\n" +
            "    }\n" +
            "    <E extends Number> void mixed(final E e, final List<String> a, final List<Integer> b) {\n" +
            "    }\n" +
            "    <N> void mixed(final Integer e, final Collection<? extends N> a, final Collection<N> b) {\n" +
            "    }\n" +
            "    <N> void mixed(final Long e, final Collection<? super N> a, final Collection<N> b) {\n" +
            "    }\n" +
            "    void wild(final Integer e, final List<String> a, final List<Integer> b) {\n" +
            "        this.mixed(e, a, b);\n" +
            "    }\n" +
            "    <E extends Number> void filled(final E e, final List<String> list, final Integer[] array) {\n" +
            "    }\n" +
            "    <N> void filled(final Integer e, final Collection<N> list, final N[] array) {\n" +
            "    }\n" +
            "    void arrayed(final Integer e, final List<String> list, final Integer[] array) {\n" +
            "        this.filled(e, list, array);\n" +
            "    }\n" +
            "    <E extends Number> void odd(final E e, final List<Odd> list) {\n" +
            "    }\n" +
            "    <N extends Comparable<N>> void odd(final Integer e, final Collection<N> list) {\n" +
            "    }\n" +
            "    <E extends Number> void tables(final E e, final List<String[]> a, final List<Integer[]> b) {\n" +
            "    }\n" +
            "    <N> void tables(final Integer e, final Collection<N[]> a, final Collection<N[]> b) {\n" +
            "    }\n" +
            "    void bounds(final Integer e, final List<Odd> odd, final List<String[]> a, final List<Integer[]> b) {\n" +
            "        this.odd(e, odd);\n" +
            "        this.tables(e, a, b);\n" +
            "    }\n" +
            "    <E extends Number> void add(final E e, final List<String> list, final Integer n) {\n" +
            "    }\n" +
            "    <N> void add(final Integer e, final Collection<N> list, final N n) {\n" +
            "    }\n" +
            "    void scalar(final Integer e, final List<String> list) {\n" +
            "        this.add(e, list, 1);\n" +
            "    }\n" +
            "    <T extends Serializable, U extends CharSequence> Coffer<T> pair(final T t, final U u) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    Coffer<String> pair(final String t, final String u) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    <T extends CharSequence> void put(final T t, final Map<? super T, Integer> map) {\n" +
            "    }\n" +
            "    void put(final String t, final Map map) {\n" +
            "    }\n" +
            "    void shared(final Integer n, final List<String> a, final List<Integer> b) {\n" +
            "        this.toss(n, a, b);\n" +
            "    }\n" +
            "    int several(final String s) {\n" +
            "        return ((CharSequence)this.pair((Serializable)s, (CharSequence)s).get()).length();\n" +
            "    }\n" +
            "    void compatible(final String s, final Map<Object, Integer> map) {\n" +
            "        this.put((CharSequence)s, map);\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testWildcardVariableBoundsAndMembersTakingClassVariables() {
        verifyOutput(
            Inputs.class,
            defaultSettings(),
            "class Inputs {\n" +
            "    <E extends Number> void toss(final E e, final List<String> list) {\n" +
            "    }\n" +
            "    <N extends Number> void toss(final Integer e, final Collection<? extends N> list) {\n" +
            "    }\n" +
            "    <T extends CharSequence> List<T> wrap(final T t) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    List<String> wrap(final String s) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    void wildcard(final Integer n, final List<String> list) {\n" +
            "        this.toss(n, list);\n" +
            "    }\n" +
            "    boolean member(final String s, final List<String> list) {\n" +
            "        return ((List)this.wrap((CharSequence)s)).addAll(list);\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testFieldsWrittenThroughWidenedReceivers() {
        verifyOutput(
            Writes.class,
            defaultSettings(),
            "class Writes {\n" +
            "    <T extends CharSequence> Crate<T> crate(final T t) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    Crate<String> crate(final String s) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    void write(final String s, final List<String> list) {\n" +
            "        this.crate((CharSequence)s).values = (List)list;\n" +
            "    }\n" +
            "    <T extends CharSequence> List<T>[] arrays(final T t) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    List<String>[] arrays(final String s) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    void element(final String s, final List<String> list) {\n" +
            "        ((List[])(List[])this.arrays((CharSequence)s))[0] = list;\n" +
            "    }\n" +
            "    <T extends Hazard<?>> T hazard(final T t) {\n" +
            "        return t;\n" +
            "    }\n" +
            "    Risk hazard(final Risk risk) {\n" +
            "        return risk;\n" +
            "    }\n" +
            "    void act(final Risk risk) throws IOException {\n" +
            "        ((Hazard<IOException>)(Hazard)this.hazard((Hazard)risk)).act();\n" +
            "    }\n" +
            "    void passed(final Risk risk, final IOException e) throws IOException {\n" +
            "        ((Hazard<IOException>)(Hazard)this.hazard((Hazard)risk)).rethrow(e);\n" +
            "    }\n" +
            "    void several(final Risk risk) throws IOException, ReflectiveOperationException {\n" +
            "        ((Hazard<IOException>)(Hazard)this.hazard((Hazard)risk)).act();\n" +
            "    }\n" +
            "    void caught(final Risk risk) {\n" +
            "        try {\n" +
            "            ((Hazard<IOException>)(Hazard)this.hazard((Hazard)risk)).act();\n" +
            "        }\n" +
            "        catch (final IOException e) {\n" +
            "            e.printStackTrace();\n" +
            "        }\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testNoCastToAnInaccessibleResultType() {
        verifyOutput(
            HiddenResult.class,
            defaultSettings(),
            "class HiddenResult {\n" +
            "    Object run(final String value) {\n" +
            "        return OverloadsElsewhere.hidden((CharSequence)value);\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testBoundsSeenFromTheCaller() {
        verifyOutput(
            TypedCalls.class,
            defaultSettings(),
            "class TypedCalls {\n" +
            "    String parameterized(final Typed<CharSequence> typed, final String s) {\n" +
            "        return (String)typed.take((CharSequence)s);\n" +
            "    }\n" +
            "    String inherited(final StringsTyped typed, final String s) {\n" +
            "        return (String)typed.take((CharSequence)s);\n" +
            "    }\n" +
            "    <T extends CharSequence> List<T>[] wrap(final T value) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    List<String>[] wrap(final String value) {\n" +
            "        return (List[])this.wrap((CharSequence)value);\n" +
            "    }\n" +
            "    int element(final String value) {\n" +
            "        return this.wrap((CharSequence)value)[0].get(0).length();\n" +
            "    }\n" +
            "    int count(final String value) {\n" +
            "        return this.wrap((CharSequence)value).length;\n" +
            "    }\n" +
            "    void store(final String value) {\n" +
            "        this.wrap((CharSequence)value)[0] = null;\n" +
            "    }\n" +
            "    void hiddenBound(final OverloadsElsewhere.Child child) {\n" +
            "        OverloadsElsewhere.take((OverloadsElsewhere.Parent)child);\n" +
            "    }\n" +
            "    <T extends CharSequence> List<T> list(final T value) {\n" +
            "        return null;\n" +
            "    }\n" +
            "    List<String> list(final String value) {\n" +
            "        return (List)this.list((CharSequence)value);\n" +
            "    }\n" +
            "    int loop(final String value) {\n" +
            "        int n = 0;\n" +
            "        for (final CharSequence c : this.list((CharSequence)value)) {\n" +
            "            n += c.length();\n" +
            "        }\n" +
            "        return n;\n" +
            "    }\n" +
            "    int iterate(final String value) {\n" +
            "        final Iterator<CharSequence> i = (Iterator)this.list((CharSequence)value).iterator();\n" +
            "        int n = 0;\n" +
            "        while (i.hasNext()) {\n" +
            "            n += i.next().length();\n" +
            "        }\n" +
            "        return n + (i.hasNext() ? 1 : 0);\n" +
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
class ClassBound<X> {
    <T extends X> T first(final T value) {
        return value;
    }

    @SuppressWarnings("unchecked")
    String first(final String value) {
        return (String) first((X) value);
    }

    <U extends X, V extends U> V second(final V value) {
        return value;
    }

    @SuppressWarnings("unchecked")
    String second(final String value) {
        return (String) second((X) value);
    }

    <T extends CharSequence> List<T>[] array(final T value) {
        return null;
    }

    @SuppressWarnings("unchecked")
    List<String>[] array(final String value) {
        return (List[]) array((CharSequence) value);
    }

    @SuppressWarnings("unchecked")
    String other(final ClassBound<CharSequence> other, final String value) {
        return (String) other.first((CharSequence) value);
    }
}

@SuppressWarnings("unused") // decompiler fixture
class Typed<X> {
    <T extends X> T take(final T value) {
        return value;
    }

    String take(final String value) {
        return value;
    }
}

@SuppressWarnings("unused") // decompiler fixture
class StringsTyped extends Typed<CharSequence> {
}

@SuppressWarnings("unused") // decompiler fixture
class TypedCalls {
    String parameterized(final Typed<CharSequence> typed, final String s) {
        return (String) typed.take((CharSequence) s);
    }

    String inherited(final StringsTyped typed, final String s) {
        return (String) typed.take((CharSequence) s);
    }

    <T extends CharSequence> List<T>[] wrap(final T value) {
        return null;
    }

    @SuppressWarnings("unchecked")
    List<String>[] wrap(final String value) {
        return (List[]) wrap((CharSequence) value);
    }

    int element(final String value) {
        return wrap((CharSequence) value)[0].get(0).length();
    }

    int count(final String value) {
        return wrap((CharSequence) value).length;
    }

    void store(final String value) {
        wrap((CharSequence) value)[0] = null;
    }

    void hiddenBound(final OverloadsElsewhere.Child child) {
        OverloadsElsewhere.take((OverloadsElsewhere.Parent) child);
    }

    <T extends CharSequence> List<T> list(final T value) {
        return null;
    }

    @SuppressWarnings("unchecked")
    List<String> list(final String value) {
        return (List) list((CharSequence) value);
    }

    int loop(final String value) {
        int n = 0;
        for (final CharSequence c : list((CharSequence) value)) {
            n += c.length();
        }
        return n;
    }

    int iterate(final String value) {
        final Iterator<CharSequence> i = list((CharSequence) value).iterator();
        int n = 0;
        while (i.hasNext()) {
            n += i.next().length();
        }
        return n + (i.hasNext() ? 1 : 0);
    }
}

@SuppressWarnings("unused") // decompiler fixture
class HiddenResult {
    Object run(final String value) {
        return OverloadsElsewhere.hidden((CharSequence) value);
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
class Funnel {
    static <E extends Exception> void toss(final E e) throws E {
        throw e;
    }

    static void toss(final FileNotFoundException e) {
    }

    static <F extends Exception, E extends F> void relay(final E e) throws F {
        throw e;
    }

    static void relay(final FileNotFoundException e) {
    }
}

@SuppressWarnings("unused") // decompiler fixture
class FunnelCaller {
    void run(final FileNotFoundException e) throws IOException {
        Funnel.toss((IOException) e);
    }

    void relayed(final FileNotFoundException e) throws IOException {
        Funnel.relay((IOException) e);
    }
}

@SuppressWarnings("unused") // decompiler fixture
class Shelf<X> {
    <T extends X> T take(final T t) {
        return t;
    }

    String take(final String s) {
        return s;
    }
}

@SuppressWarnings({ "unused", "unchecked" }) // decompiler fixture
class ShelfCaller {
    <Y> String borrow(final Shelf<Y> shelf, final String s) {
        return (String) shelf.take((Y) (Object) s);
    }
}

@SuppressWarnings({ "unused", "unchecked" }) // decompiler fixture
class Pairs {
    <T extends CharSequence, R> List<R> both(final T t) {
        return null;
    }

    List<String> both(final String s) {
        return null;
    }

    <T extends CharSequence> List<T>[] arrays(final T t) {
        return null;
    }

    List<String>[] arrays(final String s) {
        return null;
    }

    int independent(final String s) {
        return this.<CharSequence, String>both((CharSequence) s).get(0).length();
    }

    int element(final String s) {
        final List<CharSequence> list = this.arrays((CharSequence) s)[0];
        return list.size();
    }

    <T extends CharSequence> Crate<T> crate(final T t) {
        return null;
    }

    Crate<String> crate(final String s) {
        return null;
    }

    int field(final String s) {
        final List<CharSequence> values = this.crate((CharSequence) s).values;
        return values.size();
    }

    int fieldElement(final String s) {
        final List<CharSequence> list = this.crate((CharSequence) s).shelves[0];
        return list.size();
    }
}

@SuppressWarnings("unused") // decompiler fixture
class Crate<T> {
    T value;
    T[] items;

    T[] array() {
        return null;
    }

    List<T> values;
    List<T>[] shelves;
}

@SuppressWarnings({ "unused", "unchecked" }) // decompiler fixture
class Partners {
    <T extends CharSequence> void pair(final T t, final List<T> list) {
    }

    void pair(final String s, final List<String> list) {
    }

    void dependent(final String s, final List<String> list) {
        this.pair((CharSequence) s, (List) list);
    }

    String lower(final Shelf<? super CharSequence> shelf, final String s) {
        return (String) shelf.take((CharSequence) s);
    }
}

@SuppressWarnings({ "unused", "unchecked" }) // decompiler fixture
class Narrowed {
    <T extends CharSequence> Crate<T> crate(final T t) {
        return null;
    }

    Crate<String> crate(final String s) {
        return null;
    }

    String element(final String s) {
        return ((String) this.crate((CharSequence) s).values.get(0)).substring(1);
    }

    String field(final String s) {
        return ((String) this.crate((CharSequence) s).value).substring(1);
    }

    int wide(final String s) {
        return ((CharSequence) this.crate((CharSequence) s).values.get(0)).length();
    }

    <T extends CharSequence> void pair(final T t, final List<T>[] lists) {
    }

    void pair(final String s, final List<String>[] lists) {
    }

    void arrays(final String s, final List<String>[] lists) {
        this.pair((CharSequence) s, (List[]) lists);
    }
}

@SuppressWarnings({ "unused", "unchecked" }) // decompiler fixture
class Consumers {
    <T extends CharSequence> void take(final T t, final Consumer<T> consumer) {
    }

    void take(final String s, final Consumer<String> consumer) {
    }

    static void print(final String value) {
    }

    void lambda(final String s) {
        this.take((CharSequence) s, (Consumer) (Consumer<String>) (x -> print(x.substring(1))));
    }

    void reference(final String s) {
        this.take((CharSequence) s, (Consumer) (Consumer<String>) Consumers::print);
    }

    <T extends CharSequence> void count(final T t, final int n) {
    }

    void count(final String s, final String n) {
    }

    void inapplicable(final String s) {
        this.count(s, 1);
    }

    <T extends CharSequence> Crate<T> crate(final T t) {
        return null;
    }

    Crate<String> crate(final String s) {
        return null;
    }

    String array(final String s) {
        return ((String[]) this.crate((CharSequence) s).array())[0].substring(1);
    }
}

@SuppressWarnings({ "unused", "unchecked" }) // decompiler fixture
class Indexed {
    <T extends CharSequence> Crate<T> crate(final T t) {
        return null;
    }

    Crate<String> crate(final String s) {
        return null;
    }

    String element(final String s) {
        return ((String) this.crate((CharSequence) s).items[0]).substring(1);
    }

    <N extends Number> void flag(final N n, final int i) {
    }

    void flag(final Integer n, final boolean b) {
    }

    <N extends Number> void widen(final N n, final int i) {
    }

    void widen(final Integer n, final long i) {
    }

    <N extends Number> void box(final N n, final int i) {
    }

    void box(final Integer n, final Object o) {
    }

    void primitives(final Integer n) {
        this.flag(n, 1);
        this.widen((Number) n, 1);
        this.box((Number) n, 1);
    }
}

@SuppressWarnings({ "unused", "unchecked" }) // decompiler fixture
class Chained {
    static List<String> shared;
    List<String> field;

    <T extends CharSequence> List<T> wrap(final T t) {
        return null;
    }

    List<String> wrap(final String s) {
        return null;
    }

    static void use(final List<String> list) {
    }

    List<String> returned(final String s) {
        return (List) this.wrap((CharSequence) s).subList(0, 1);
    }

    void passed(final String s) {
        use((List) this.wrap((CharSequence) s).subList(0, 1));
    }

    void stored(final String s, final List<String>[] lists) {
        this.field = (List) this.wrap((CharSequence) s).subList(0, 1);
        shared = (List) this.wrap((CharSequence) s).subList(0, 1);
        lists[0] = (List) this.wrap((CharSequence) s).subList(0, 1);
    }

    <N extends Number> void constrained(final N n, final List<Integer> list) {
    }

    void constrained(final Integer n, final Collection<String> list) {
    }

    <N extends Integer> void specific(final N n) {
    }

    void specific(final Number n) {
    }

    void direct(final Integer n, final List<Integer> list) {
        this.constrained(n, list);
        this.specific(n);
    }
}

@SuppressWarnings({ "unused", "unchecked" }) // decompiler fixture
class Restricted {
    <T extends CharSequence> T[] array(final T t) {
        return null;
    }

    String[] array(final String s) {
        return null;
    }

    String element(final String s) {
        return ((String) this.array((CharSequence) s)[0]).substring(1);
    }

    <N extends Number> void arrays(final N n, final List<Integer>[] lists) {
    }

    void arrays(final Integer n, final Collection<String>[] lists) {
    }

    void protectedElsewhere(final OverloadsElsewhere other, final Integer n, final String s) {
        OverloadsElsewhere.count(n);
        other.send(s);
    }

    void direct(final Integer n, final List<Integer>[] lists) {
        this.arrays(n, lists);
    }
}

@SuppressWarnings("unused") // decompiler fixture
class RestrictedHeir extends OverloadsElsewhere {
    void through(final OverloadsElsewhere other, final String s) {
        other.send(s);
        this.send((CharSequence) s);
    }
}

@SuppressWarnings("unused") // decompiler fixture
class Coffer<X> {
    X get() {
        return null;
    }
}

@SuppressWarnings("unused") // decompiler fixture
class Casket extends Coffer<String> {
}

@SuppressWarnings({ "unused", "unchecked" }) // decompiler fixture
class Coordinated {
    <T extends Coffer<?>> T take(final T t) {
        return t;
    }

    Casket take(final Casket casket) {
        return casket;
    }

    String scalar(final Casket casket) {
        return ((String) this.take((Coffer<?>) casket).get()).substring(1);
    }

    <T extends CharSequence> List<T> wrap(final T t) {
        return null;
    }

    List<String> wrap(final String s) {
        return null;
    }

    List<String> conditional(final boolean flag, final String s, final List<String> other) {
        return flag ? (List) this.wrap((CharSequence) s).subList(0, 1) : other;
    }

    <N extends Number> void bounded(final N n, final List<? extends Number> list) {
    }

    void bounded(final Integer n, final Collection<Integer> list) {
    }

    <E extends Exception, R> void toss(final E e, final R r) throws E {
    }

    void toss(final IOException e, final String s) {
    }

    void direct(final Integer n, final List<? extends Number> list, final IOException e, final String s) throws IOException {
        this.bounded(n, list);
        this.toss(e, (Object) s);
    }
}

@SuppressWarnings({ "unused", "unchecked" }) // decompiler fixture
class Shadows {
    <E extends Number> void toss(final E e, final String s) {
    }

    <N extends Number> void toss(final Integer e, final N n) {
    }

    List<String> shaded(final String s) {
        return (List) OverloadsElsewhere.shaded((CharSequence) s);
    }

    void direct(final Integer n) {
        this.toss(n, "x");
    }
}

@SuppressWarnings({ "unused", "unchecked" }) // decompiler fixture
class Forked {
    <E extends Number> void toss(final E e, final List<Integer> list) {
    }

    <N extends Collection<String>> void toss(final Integer e, final N n) {
    }

    <T extends CharSequence, R> List<R> wrap(final T t, final Map<T, R> map) {
        return null;
    }

    List<CharSequence> wrap(final String s, final Map<String, CharSequence> map) {
        return null;
    }

    <T extends CharSequence> List<T> single(final T t) {
        return null;
    }

    List<String> single(final String s) {
        return null;
    }

    void bounded(final Integer n, final List<Integer> list) {
        this.toss(n, list);
    }

    int raw(final String s, final Map<String, CharSequence> map) {
        return ((CharSequence) this.wrap((CharSequence) s, (Map) map).get(0)).length();
    }

    String chosen(final boolean flag, final String s, final List<String> other) {
        return ((String) (flag ? (List) this.single((CharSequence) s) : other).get(0)).substring(1);
    }
}

@SuppressWarnings({ "unused", "unchecked" }) // decompiler fixture
class Partial {
    <E extends Number> void toss(final E e, final Map<Integer, Integer> map) {
    }

    <N> void toss(final Integer e, final Map<String, N> map) {
    }

    <T extends CharSequence & Serializable, R> List<R> wrap(final T t) {
        return null;
    }

    List<String> wrap(final String s) {
        return null;
    }

    void partly(final Integer n, final Map<Integer, Integer> map) {
        this.toss(n, map);
    }

    int uninferred(final String s) {
        return ((CharSequence) this.wrap((CharSequence & Serializable) s).get(0)).length();
    }
}

@SuppressWarnings({ "unused", "unchecked" }) // decompiler fixture
class Nested2 {
    <E extends Number> void toss(final E e, final List<String> list) {
    }

    <N extends Number> void toss(final Integer e, final Collection<N> list) {
    }

    <T extends CharSequence> List<T> take(final T t) {
        return null;
    }

    List<String> take(final String s) {
        return null;
    }

    void nested(final Integer n, final List<String> list) {
        this.toss(n, list);
    }

    void items(final String s) {
        for (final Serializable item : (List<String>) (List) this.take((CharSequence) s)) {
            System.out.println(item.hashCode());
        }
    }
}

@SuppressWarnings({ "unused", "unchecked" }) // decompiler fixture
class Shared {
    <E extends Number> void toss(final E e, final List<String> a, final List<Integer> b) {
    }

    <N> void toss(final Integer e, final Collection<N> a, final Collection<N> b) {
    }

    <E extends Number> void deep(final E e, final List<List<String>> lists) {
    }

    <N extends Number> void deep(final Integer e, final Collection<List<N>> lists) {
    }

    void nested(final Integer e, final List<List<String>> lists) {
        this.deep(e, lists);
    }

    <E extends Number> void apart(final E e, final List<List<String>> a, final List<List<Integer>> b) {
    }

    <N> void apart(final Integer e, final Collection<List<N>> a, final Collection<List<N>> b) {
    }

    void separate(final Integer e, final List<List<String>> a, final List<List<Integer>> b) {
        this.apart(e, a, b);
    }

    <E extends Number> void under(final E e, final List<String> list) {
    }

    <N extends Number> void under(final Integer e, final Collection<? super N> list) {
    }

    void below(final Integer e, final List<String> list) {
        this.under(e, list);
    }

    <E extends Number> void mixed(final E e, final List<String> a, final List<Integer> b) {
    }

    <N> void mixed(final Integer e, final Collection<? extends N> a, final Collection<N> b) {
    }

    <N> void mixed(final Long e, final Collection<? super N> a, final Collection<N> b) {
    }

    void wild(final Integer e, final List<String> a, final List<Integer> b) {
        this.mixed(e, a, b);
    }

    <E extends Number> void filled(final E e, final List<String> list, final Integer[] array) {
    }

    <N> void filled(final Integer e, final Collection<N> list, final N[] array) {
    }

    void arrayed(final Integer e, final List<String> list, final Integer[] array) {
        this.filled(e, list, array);
    }

    <E extends Number> void odd(final E e, final List<Odd> list) {
    }

    <N extends Comparable<N>> void odd(final Integer e, final Collection<N> list) {
    }

    <E extends Number> void tables(final E e, final List<String[]> a, final List<Integer[]> b) {
    }

    <N> void tables(final Integer e, final Collection<N[]> a, final Collection<N[]> b) {
    }

    void bounds(final Integer e, final List<Odd> odd, final List<String[]> a, final List<Integer[]> b) {
        this.odd(e, odd);
        this.tables(e, a, b);
    }

    <E extends Number> void add(final E e, final List<String> list, final Integer n) {
    }

    <N> void add(final Integer e, final Collection<N> list, final N n) {
    }

    void scalar(final Integer e, final List<String> list) {
        this.add(e, list, 1);
    }

    <T extends Serializable, U extends CharSequence> Coffer<T> pair(final T t, final U u) {
        return null;
    }

    Coffer<String> pair(final String t, final String u) {
        return null;
    }

    <T extends CharSequence> void put(final T t, final Map<? super T, Integer> map) {
    }

    void put(final String t, final Map map) {
    }

    void shared(final Integer n, final List<String> a, final List<Integer> b) {
        this.toss(n, a, b);
    }

    int several(final String s) {
        return ((CharSequence) this.pair((Serializable) s, (CharSequence) s).get()).length();
    }

    void compatible(final String s, final Map<Object, Integer> map) {
        this.put((CharSequence) s, map);
    }
}

@SuppressWarnings({ "unused", "unchecked" }) // decompiler fixture
class Inputs {
    <E extends Number> void toss(final E e, final List<String> list) {
    }

    <N extends Number> void toss(final Integer e, final Collection<? extends N> list) {
    }

    <T extends CharSequence> List<T> wrap(final T t) {
        return null;
    }

    List<String> wrap(final String s) {
        return null;
    }

    void wildcard(final Integer n, final List<String> list) {
        this.toss(n, list);
    }

    boolean member(final String s, final List<String> list) {
        return ((List<String>) (List) this.wrap((CharSequence) s)).addAll(list);
    }
}

@SuppressWarnings({ "unused", "unchecked" }) // decompiler fixture
class Writes {
    <T extends CharSequence> Crate<T> crate(final T t) {
        return null;
    }

    Crate<String> crate(final String s) {
        return null;
    }

    void write(final String s, final List<String> list) {
        ((Crate<String>) (Crate) this.crate((CharSequence) s)).values = list;
    }

    <T extends CharSequence> List<T>[] arrays(final T t) {
        return null;
    }

    List<String>[] arrays(final String s) {
        return null;
    }

    void element(final String s, final List<String> list) {
        ((List<String>[]) (List[]) this.arrays((CharSequence) s))[0] = list;
    }

    <T extends Hazard<?>> T hazard(final T t) {
        return t;
    }

    Risk hazard(final Risk risk) {
        return risk;
    }

    void act(final Risk risk) throws IOException {
        ((Hazard<IOException>) (Hazard) this.hazard((Hazard<?>) risk)).act();
    }

    void passed(final Risk risk, final IOException e) throws IOException {
        ((Hazard<IOException>) (Hazard) this.hazard((Hazard<?>) risk)).rethrow(e);
    }

    void several(final Risk risk) throws IOException, ReflectiveOperationException {
        ((Hazard<IOException>) (Hazard) this.hazard((Hazard<?>) risk)).act();
    }

    void caught(final Risk risk) {
        try {
            ((Hazard<IOException>) (Hazard) this.hazard((Hazard<?>) risk)).act();
        }
        catch (final IOException e) {
            e.printStackTrace();
        }
    }
}

@SuppressWarnings("unused") // decompiler fixture
abstract class Hazard<X extends Exception> {
    abstract void act() throws X;

    abstract void rethrow(X cause) throws X;
}

@SuppressWarnings("unused") // decompiler fixture
abstract class Risk extends Hazard<IOException> {
}

@SuppressWarnings("unused") // decompiler fixture
abstract class Odd implements Comparable<String> {
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

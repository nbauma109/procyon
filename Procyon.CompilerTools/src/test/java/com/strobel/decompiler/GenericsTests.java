package com.strobel.decompiler;

import com.strobel.decompiler.fixtures.OverloadsElsewhere;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.stream.Stream;
import java.util.function.Supplier;
import java.util.function.Function;
import java.util.Optional;
import java.util.Collections;
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
            "    int element(final String s) {\n" +
            "        final CharSequence s2 = this.array((CharSequence)s)[0];\n" +
            "        return s2.length() + s2.charAt(0);\n" +
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
            "        return this.<CharSequence, String>both((CharSequence)s).get(0).length();\n" +
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

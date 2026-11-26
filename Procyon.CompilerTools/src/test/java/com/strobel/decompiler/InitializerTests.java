package com.strobel.decompiler;

import static org.junit.Assert.assertEquals;
import java.util.Arrays;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.File;
import javax.tools.ToolProvider;
import javax.tools.JavaCompiler;
import org.junit.rules.TemporaryFolder;
import org.junit.Rule;
import org.junit.Test;

public class InitializerTests extends DecompilerTest {
    private static class A {
        static final int c = 42;
        final int i = 42;
    }

    @Test
    public void testConstantNotInitializedInConstructor() throws Throwable {
        verifyOutput(
            A.class,
            defaultSettings(),
            "private static class A {\n" +
            "    static final int c = 42;\n" +
            "    final int i = 42;\n" +
            "}\n"
        );
    }

    @Test
    public void testInitializersReadingFieldsAssignedByTheConstructorStayThere() {
        verifyOutput(
            ReadsConstructorField.class,
            defaultSettings(),
            "static class ReadsConstructorField {\n" +
            "    private final char[] delimiter;\n" +
            "    ReadsConstructorField(final String text) {\n" +
            "        this.delimiter = text.toCharArray();\n" +
            "        this.buffer = new char[this.delimiter.length];\n" +
            "        this.size = this.buffer.length;\n" +
            "    }\n" +
            "    private final Object lock = new Object();\n" +
            "    private final char[] buffer;\n" +
            "    private final int size;\n" +
            "}\n"
        );
    }

    static class ReadsConstructorField {
        private final char[] delimiter;
        private final char[] buffer;
        private final int size;
        private final Object lock;

        ReadsConstructorField(final String text) {
            this.lock = new Object();
            this.delimiter = text.toCharArray();
            this.buffer = new char[this.delimiter.length];
            this.size = this.buffer.length;
        }
    }

    @Test
    public void testFieldsAssignedSeveralTimesAreNotMoved() {
        verifyOutput(
            AssignedTwice.class,
            defaultSettings(),
            "static class AssignedTwice {\n" +
            "    AssignedTwice(final int value) {\n" +
            "        this.twice = 1;\n" +
            "        this.twice = 2;\n" +
            "        this.parameterFirst = value;\n" +
            "        this.parameterFirst = 3;\n" +
            "        this.reader = this.twice;\n" +
            "    }\n" +
            "    private int twice;\n" +
            "    private int parameterFirst;\n" +
            "    private final int reader;\n" +
            "}\n"
        );
    }

    @Test
    public void testInitializersMayReadFieldsKeepingTheirDefaultValue() {
        verifyOutput(
            ReadsDefaultValue.class,
            defaultSettings(),
            "static class ReadsDefaultValue {\n" +
            "    private int unset;\n" +
            "    ReadsDefaultValue() {\n" +
            "    }\n" +
            "    private final int first = this.unset + 1;\n" +
            "    private final int second = this.readFirst();\n" +
            "    private int readFirst() {\n" +
            "        return this.first;\n" +
            "    }\n" +
            "}\n"
        );
    }

    static class AssignedTwice {
        private int twice;
        private int parameterFirst;
        private final int reader;

        AssignedTwice(final int value) {
            this.twice = 1;
            this.twice = 2;
            this.parameterFirst = value;
            this.parameterFirst = 3;
            this.reader = this.twice;
        }
    }

    static class ReadsDefaultValue {
        private int unset;
        private final int first;
        private final int second;

        ReadsDefaultValue() {
            this.first = this.unset + 1;
            this.second = this.readFirst();
        }

        private int readFirst() {
            return this.first;
        }
    }

    @Test
    public void testInitializersDoNotOvertakeTheStatementsTheyDependOn() {
        verifyOutput(
            CallsAfterAssignments.class,
            defaultSettings(),
            "static class CallsAfterAssignments {\n" +
            "    CallsAfterAssignments() {\n" +
            "        this.x = 1;\n" +
            "        this.x = 2;\n" +
            "        this.y = this.readX();\n" +
            "    }\n" +
            "    private int x;\n" +
            "    private final int y;\n" +
            "    private int readX() {\n" +
            "        return this.x;\n" +
            "    }\n" +
            "}\n"
        );
    }

    @Test
    public void testAssignmentsDoNotOvertakeEarlierReads() {
        verifyOutput(
            ReadBeforeWrite.class,
            defaultSettings(),
            "static class ReadBeforeWrite {\n" +
            "    ReadBeforeWrite() {\n" +
            "        this.x = 1;\n" +
            "        this.x = 2;\n" +
            "        this.y = this.x + this.z;\n" +
            "        this.z = 3;\n" +
            "    }\n" +
            "    private int x;\n" +
            "    private final int y;\n" +
            "    private int z;\n" +
            "}\n"
        );
    }

    static class CallsAfterAssignments {
        private int x;
        private final int y;

        CallsAfterAssignments() {
            this.x = 1;
            this.x = 2;
            this.y = this.readX();
        }

        private int readX() {
            return this.x;
        }
    }

    static class ReadBeforeWrite {
        private int x;
        private final int y;
        private int z;

        ReadBeforeWrite() {
            this.x = 1;
            this.x = 2;
            this.y = this.x + this.z;
            this.z = 3;
        }
    }

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void testInitializersUsingParametersStayInTheConstructorWithoutLocalVariableNames() throws IOException {
        final File source = folder.newFile("NoVariableNames.java");

        Files.write(
            source.toPath(),
            Arrays.asList(
                "public class NoVariableNames {",
                "    private int first;",
                "    private final int second;",
                "    private final Object third = new Object();",
                "    NoVariableNames(int i) {",
                "        first = i;",
                "        if (i < 0) throw new IllegalArgumentException();",
                "        second = i / 3;",
                "    }",
                "}"
            ),
            StandardCharsets.UTF_8
        );

        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        // line numbers, but no names for the local variables
        assertEquals(0, compiler.run(null, null, null, "-g:lines,source", "-d", folder.getRoot().getPath(), source.getPath()));

        verifyOutput(
            new File(folder.getRoot(), "NoVariableNames.class").getPath(),
            defaultSettings(),
            "public class NoVariableNames {\n" +
            "    private int first;\n" +
            "    private final int second;\n" +
            "    private final Object third = new Object();\n" +
            "    NoVariableNames(final int first) {\n" +
            "        this.first = first;\n" +
            "        if (first < 0) {\n" +
            "            throw new IllegalArgumentException();\n" +
            "        }\n" +
            "        this.second = first / 3;\n" +
            "    }\n" +
            "}\n"
        );
    }
}


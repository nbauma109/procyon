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


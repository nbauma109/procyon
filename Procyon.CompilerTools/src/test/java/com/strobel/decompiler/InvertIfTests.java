/*
 * InvertIfTests.java
 *
 * Copyright (c) 2013 Mike Strobel
 *
 * This source code is subject to terms and conditions of the Apache License, Version 2.0.
 * A copy of the license can be found in the License.html file at the root of this distribution.
 * By using this source code in any fashion, you are agreeing to be bound by the terms of the
 * Apache License, Version 2.0.
 *
 * You must not remove this notice, or any other, from this software.
 */

package com.strobel.decompiler;

import org.junit.Test;

public class InvertIfTests extends DecompilerTest {
    private class T {
        void main(final String[] args) {
            if (args.length == 0) {
                System.out.println("no arg");
            } else {
                if (args.length == 1) {
                    System.out.println("1 arg");
                } else {
                    throw new RuntimeException("too many args");
                }
            }
        }
    }

    private class U {
        void test(final Object o) {
            if (o instanceof String) {
                System.out.println(o);
            } else {
                if (o instanceof CharSequence) {
                    System.out.println(o);
                } else {
                    throw new RuntimeException();
                }
            }
        }
    }

    @Test
    public void testInvertIfTest() {
        verifyOutput(
            T.class,
            lineNumberSettings(),
            "private class T\n" +
            "{\n" +
            "    void main(final String[] args) {\n" +
            "        /*SL:21*/if (args.length == 0) {\n" +
            "            System.out.println(/*EL:22*/\"no arg\");\n" +
            "        } else {\n" +
            "            /*SL:24*/if (args.length == 1) {\n" +
            "                System.out.println(/*EL:25*/\"1 arg\");\n" +
            "            } else {\n" +
            "                /*SL:27*/throw new RuntimeException(\"too many args\");\n" +
            "            }\n" +
            "        }\n" +
            "    }\n" +
            "}"
        );
    }

    @Test
    public void testInvertIfDoubleNegationTest() {
        verifyOutput(
            U.class,
            lineNumberSettings(),
            "private class U\n" +
            "{\n" +
            "    void test(final Object o) {\n" +
            "        /*SL:35*/if (o instanceof String) {\n" +
            "            System.out.println(/*EL:36*/o);\n" +
            "        } else {\n" +
            "            /*SL:38*/if (o instanceof CharSequence) {\n" +
            "                System.out.println(/*EL:39*/o);\n" +
            "            } else {\n" +
            "                /*SL:41*/throw new RuntimeException();\n" +
            "            }\n" +
            "        }\n" +
            "    }\n" +
            "}"
        );
    }

    
    private DecompilerSettings lineNumberSettings() {
        DecompilerSettings lineNumberSettings = defaultSettings();
        lineNumberSettings.setShowDebugLineNumbers(true);
        return lineNumberSettings;
    }

    @Test
    public void testInvertsOnlyWhenTheBranchEndsWithAJump() {
        verifyOutput(
            LoopUpdates.class,
            defaultSettings(),
            "class LoopUpdates\n" +
            "{\n" +
            "    char[] fallsThrough(final CharSequence text, final int[] indexes, final int length) {\n" +
            "        final char[] result = new char[length];\n" +
            "        int i = 0;\n" +
            "        int n = 0;\n" +
            "        while (i < text.length()) {\n" +
            "            if (indexes[i] != -1) {\n" +
            "                result[n] = text.charAt(i);\n" +
            "                ++n;\n" +
            "            }\n" +
            "            ++i;\n" +
            "        }\n" +
            "        return result;\n" +
            "    }\n" +
            "    int returns(final int[] values) {\n" +
            "        int i = 0;\n" +
            "        int n = 0;\n" +
            "        while (i < values.length) {\n" +
            "            if (values[i] >= 0) {\n" +
            "                ++i;\n" +
            "            }\n" +
            "            else {\n" +
            "                return ++n;\n" +
            "            }\n" +
            "        }\n" +
            "        return i;\n" +
            "    }\n" +
            "    int throwsOrBreaks(final int[] values, final boolean strict) {\n" +
            "        int i = 0;\n" +
            "        int n = 0;\n" +
            "        while (i < values.length) {\n" +
            "            if (values[i] < 0) {\n" +
            "                ++n;\n" +
            "                if (strict) {\n" +
            "                    throw new IllegalArgumentException(String.valueOf(n));\n" +
            "                }\n" +
            "                break;\n" +
            "            } else {\n" +
            "                ++i;\n" +
            "            }\n" +
            "        }\n" +
            "        return i;\n" +
            "    }\n" +
            "    int endsWithBranchesWhichJump(final int[] values, final boolean strict) {\n" +
            "        int i = 0;\n" +
            "        while (i < values.length) {\n" +
            "            if (values[i] < 0) {\n" +
            "                if (strict) {\n" +
            "                    throw new IllegalArgumentException();\n" +
            "                }\n" +
            "                return -1;\n" +
            "            } else {\n" +
            "                ++i;\n" +
            "            }\n" +
            "        }\n" +
            "        return i;\n" +
            "    }\n" +
            "}\n" +
            ""
        );
    }
}

@SuppressWarnings("unused") // decompiler fixture
class LoopUpdates {
    char[] fallsThrough(final CharSequence text, final int[] indexes, final int length) {
        final char[] result = new char[length];
        for (int i = 0, n = 0; i < text.length(); i++) {
            if (indexes[i] != -1) {
                result[n] = text.charAt(i);
                n++;
            }
        }
        return result;
    }

    int returns(final int[] values) {
        int i = 0;
        for (int n = 0; i < values.length; i++) {
            if (values[i] < 0) {
                n++;
                return n;
            }
        }
        return i;
    }

    int throwsOrBreaks(final int[] values, final boolean strict) {
        int i = 0;
        for (int n = 0; i < values.length; i++) {
            if (values[i] < 0) {
                n++;
                if (strict) {
                    throw new IllegalArgumentException(String.valueOf(n));
                } else {
                    break;
                }
            }
        }
        return i;
    }

    int endsWithBranchesWhichJump(final int[] values, final boolean strict) {
        int i = 0;
        for (; i < values.length; i++) {
            if (values[i] < 0) {
                if (strict) {
                    throw new IllegalArgumentException();
                } else {
                    return -1;
                }
            }
        }
        return i;
    }
}

package com.strobel.decompiler.fixtures;

/** Overloads whose accessibility depends on the package and the superclass of the caller. */
@SuppressWarnings("unused") // decompiler fixture
public class OverloadsElsewhere {
    public static <N extends Number> void count(final N n) {
    }

    static void count(final Integer n) {
    }

    public static <C extends CharSequence> void read(final C c) {
    }

    protected static void read(final String s) {
    }
}

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

    public <C extends CharSequence> void send(final C c) {
    }

    protected void send(final String s) {
    }

    public static <C extends CharSequence> Shaded<C> shaded(final C c) {
        return null;
    }

    public static Shaded<String> shaded(final String s) {
        return null;
    }

    public static class Parent extends Hidden<String> {
    }

    public static class Child extends Parent {
    }

    public static <H extends Hidden<?>> void take(final H h) {
    }

    public static void take(final Child child) {
    }

    public static <C extends CharSequence> Hidden<C> hidden(final C c) {
        return null;
    }

    public static Hidden<String> hidden(final String s) {
        return null;
    }
}

class Hidden<T> {
}

@SuppressWarnings("unused") // decompiler fixture
class Shaded<T> extends java.util.ArrayList<T> {
    private static final long serialVersionUID = 1L;
}

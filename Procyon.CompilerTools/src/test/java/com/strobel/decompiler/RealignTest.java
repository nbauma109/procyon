package com.strobel.decompiler;

import org.junit.Test;

public class RealignTest extends DecompilerTest {

    static class ReOrderMembers {
        
        public void method1() {
            System.out.println("Test.method1");
        }
        
        public void method2() {
            System.out.println("Test.method2");
        }
        
        class Inner1 {
            
            public Inner1() {
                System.out.println("Inner1 constructor");
            }
            
            public void method1() {
                System.out.println("Inner1.method1");
            }
            
            public void method2() {
                System.out.println("Inner1.method2");
            }
        }

        class Inner2 {
            
            public Inner2() {
                System.out.println("Inner2 constructor");
            }
        }


        public void method3() {
            System.out.println("Test.method3");
        }
        
        public void method4() {
            System.out.println("Test.method4");
        }

        class Inner3 {
            
            public Inner3() {
                System.out.println("Inner3 constructor");
            }
            
            public void method1() {
                System.out.println("Inner3.method1");
            }
            
            public void method2() {
                System.out.println("Inner3.method2");
            }
        }

        class Inner4 {
            
            public Inner4() {
                System.out.println("Inner4 constructor");
            }
            
            public void method1() {
                System.out.println("Inner4.method1");
            }
            
            public void method2() {
                System.out.println("Inner4.method2");
            }
        }

        public void method5() {
            System.out.println("Test.method5");
        }
        
        public void method6() {
            System.out.println("Test.method6");
        }
    }
    
    static class RewriteInit {

        private RewriteInit top = new RewriteInit(0);
        private RewriteInit test;

        static {
            System.out.println("clinit1");
        }

        public RewriteInit(int i) {
            System.out.println(i);
        }

        private RewriteInit middle = new RewriteInit(false);

        public RewriteInit(boolean flag) {
            System.out.println(flag);
            test = new RewriteInit(0); // must not be moved to declaration
        }

        static {
            System.out.println("clinit2");
        }

        private RewriteInit bottom = new RewriteInit(true);

        static {
            System.out.println("clinit3");
        }
    } 
    
    static class RewriteInit2 {
        
        private RewriteInit2 top = new RewriteInit2(0);
        private RewriteInit2 test;
        
        static {
            System.out.println("clinit1");
        }
        
        public RewriteInit2(int i) {
            this(false);
            System.out.println(i);
        }
        
        private RewriteInit2 middle = new RewriteInit2(false);
        
        public RewriteInit2(boolean flag) {
            System.out.println(flag);
            test = new RewriteInit2(0); // can be moved to declaration (because of this(...) call)
        }
        
        static {
            System.out.println("clinit2");
        }
        
        private RewriteInit2 bottom = new RewriteInit2(true);
        
        static {
            System.out.println("clinit3");
        }
        
        class Inner {
            Inner() {
                System.out.println("Inner");
            }
        }
    } 
    
    static class RewriteInit3 {
        
        private int _a, _b;
        
        public RewriteInit3(int a) {
            _a = a;
            double b = Math.random();
            _b = b < 0.5 ? 1 : 0;
        }
    }
    

    @Test
    public void testReOrderMembers() throws Throwable {
        verifyOutput(
            ReOrderMembers.class,
            lineNumberSettings(),
            "static class ReOrderMembers {\n" +
            "    public void method1() {\n" +
            "        System.out.println(/*EL:10*/\"Test.method1\");\n" +
            "    }\n" +
            "    \n" +
            "    public void method2() {\n" +
            "        System.out.println(/*EL:14*/\"Test.method2\");\n" +
            "    }\n" +
            "    \n" +
            "    class Inner1 {\n" +
            "        public Inner1() {\n" +
            "            System.out.println(/*EL:20*/\"Inner1 constructor\");\n" +
            "        }\n" +
            "    \n" +
            "        public void method1() {\n" +
            "            System.out.println(/*EL:24*/\"Inner1.method1\");\n" +
            "        }\n" +
            "    \n" +
            "        public void method2() {\n" +
            "            System.out.println(/*EL:28*/\"Inner1.method2\");\n" +
            "        }\n" +
            "    }\n" +
            "    \n" +
            "    class Inner2 {\n" +
            "        public Inner2() {\n" +
            "            System.out.println(/*EL:35*/\"Inner2 constructor\");\n" +
            "        }\n" +
            "    }\n" +
            "    \n" +
            "    public void method3() {\n" +
            "        System.out.println(/*EL:41*/\"Test.method3\");\n" +
            "    }\n" +
            "    \n" +
            "    public void method4() {\n" +
            "        System.out.println(/*EL:45*/\"Test.method4\");\n" +
            "    }\n" +
            "    \n" +
            "    class Inner3 {\n" +
            "        public Inner3() {\n" +
            "            System.out.println(/*EL:51*/\"Inner3 constructor\");\n" +
            "        }\n" +
            "    \n" +
            "        public void method1() {\n" +
            "            System.out.println(/*EL:55*/\"Inner3.method1\");\n" +
            "        }\n" +
            "    \n" +
            "        public void method2() {\n" +
            "            System.out.println(/*EL:59*/\"Inner3.method2\");\n" +
            "        }\n" +
            "    }\n" +
            "    \n" +
            "    class Inner4 {\n" +
            "        public Inner4() {\n" +
            "            System.out.println(/*EL:66*/\"Inner4 constructor\");\n" +
            "        }\n" +
            "    \n" +
            "        public void method1() {\n" +
            "            System.out.println(/*EL:70*/\"Inner4.method1\");\n" +
            "        }\n" +
            "    \n" +
            "        public void method2() {\n" +
            "            System.out.println(/*EL:74*/\"Inner4.method2\");\n" +
            "        }\n" +
            "    }\n" +
            "    \n" +
            "    public void method5() {\n" +
            "        System.out.println(/*EL:79*/\"Test.method5\");\n" +
            "    }\n" +
            "    \n" +
            "    public void method6() {\n" +
            "        System.out.println(/*EL:83*/\"Test.method6\");\n" +
            "    }\n" +
            "}"
        );
    }
    
    
    @Test
    public void testRewriteInit() throws Throwable {
        verifyOutput(
            RewriteInit.class,
            lineNumberSettings(),
            "static class RewriteInit {\n" +
            "    /*SL:89*/private RewriteInit top = new RewriteInit(0);\n" +
            "    static {\n" +
            "        System.out.println(/*EL:93*/\"clinit1\");\n" +
            "    }\n" +
            "    \n" +
            "    public RewriteInit(final int i) {\n" +
            "        System.out.println(/*EL:97*/i);\n" +
            "    }\n" +
            "    \n" +
            "    /*SL:100*/private RewriteInit middle = new RewriteInit(false);\n" +
            "    \n" +
            "    public RewriteInit(final boolean flag) {\n" +
            "        System.out.println(/*EL:103*/flag);\n" +
            "        /*SL:104*/this.test = new RewriteInit(0);\n" +
            "    }\n" +
            "    \n" +
            "    private RewriteInit test;\n" +
            "    static {\n" +
            "        System.out.println(/*EL:108*/\"clinit2\");\n" +
            "    }\n" +
            "    /*SL:111*/private RewriteInit bottom = new RewriteInit(true);\n" +
            "    static {\n" +
            "        System.out.println(/*EL:114*/\"clinit3\");\n" +
            "    }\n" +
            "}"
        );
    }
    
    @Test
    public void testRewriteInit2() throws Throwable {
        verifyOutput(
                RewriteInit2.class,
                lineNumberSettings(),
                "static class RewriteInit2 {\n" +
                        "    /*SL:120*/private RewriteInit2 top = new RewriteInit2(0);\n" +
                        "    static {\n" +
                        "        System.out.println(/*EL:124*/\"clinit1\");\n" +
                        "    }\n" +
                        "    \n" +
                        "    public RewriteInit2(final int i) {\n" +
                        "        /*SL:128*/this(false);\n" +
                        "        System.out.println(/*EL:129*/i);\n" +
                        "    }\n" +
                        "    \n" +
                        "    /*SL:132*/private RewriteInit2 middle = new RewriteInit2(false);\n" +
                        "    \n" +
                        "    public RewriteInit2(final boolean flag) {\n" +
                        "        System.out.println(/*EL:135*/flag);\n" +
                        "    }\n" +
                        "    /*SL:136*/private RewriteInit2 test = new RewriteInit2(0);\n" +
                        "    static {\n" +
                        "        System.out.println(/*EL:140*/\"clinit2\");\n" +
                        "    }\n" +
                        "    /*SL:143*/private RewriteInit2 bottom = new RewriteInit2(true);\n" +
                        "    static {\n" +
                        "        System.out.println(/*EL:146*/\"clinit3\");\n" +
                        "    }\n" +
                        "   class Inner {\n" +
                        "       Inner() {\n" +
                        "           System.out.println(/*EL:151*/\"Inner\");\n" +
                        "       }\n" +
                        "   }\n" +
                        "}"
                );
    }
    
    @Test
    public void testRewriteInit3() throws Throwable {
        verifyOutput(
                RewriteInit3.class,
                lineNumberSettings(),
                "static class RewriteInit3 {\n" +
                "    private int _a;\n" +
                "    private int _b;\n" +
                "    public RewriteInit3(final int a) {\n" +
                "        /*SL:161*/this._a = a;\n" +
                "        final double b = /*EL:162*/Math.random();\n" +
                "        /*SL:163*/this._b = ((b < 0.5) ? 1 : 0);\n" +
                "    }\n" +
                "}"
        );
    }
    
    @Test
    public void testRewriteInitThrowable() throws Throwable {
    	verifyOutput(
    			"java/lang/Throwable",
    			lineNumberSettings(),
    			"public class Throwable implements Serializable { private static final long serialVersionUID = -3042686055658047285L; private transient Object backtrace; private static final StackTraceElement[] UNASSIGNED_STACK; private StackTraceElement[] stackTrace; private static final List<Throwable> SUPPRESSED_SENTINEL; private List<Throwable> suppressedExceptions; private static final String NULL_CAUSE_MESSAGE = \"Cannot suppress a null exception.\"; private static final String SELF_SUPPRESSION_MESSAGE = \"Self-suppression not permitted\"; private static final String CAUSE_CAPTION = \"Caused by: \"; private static final String SUPPRESSED_CAPTION = \"Suppressed: \"; private static final Throwable[] EMPTY_THROWABLE_ARRAY; private native Throwable fillInStackTrace(final int p0); native int getStackTraceDepth(); native StackTraceElement getStackTraceElement(final int p0); private abstract static class PrintStreamOrWriter { abstract Object lock(); abstract void println(final Object p0); } private static class SentinelHolder { public static final StackTraceElement STACK_TRACE_ELEMENT_SENTINEL; public static final StackTraceElement[] STACK_TRACE_SENTINEL; static { /*SL:146*/STACK_TRACE_ELEMENT_SENTINEL = new StackTraceElement(\"\", \"\", null, Integer.MIN_VALUE); } static { /*SL:153*/STACK_TRACE_SENTINEL = new StackTraceElement[] { SentinelHolder.STACK_TRACE_ELEMENT_SENTINEL }; } } static { /*SL:160*/UNASSIGNED_STACK = new StackTraceElement[0]; } static { /*SL:216*/SUPPRESSED_SENTINEL = Collections.unmodifiableList((List<? extends Throwable>)new ArrayList<Throwable>(0)); } public Throwable() { /*SL:211*/this.stackTrace = Throwable.UNASSIGNED_STACK; /*SL:228*/this.suppressedExceptions = Throwable.SUPPRESSED_SENTINEL; /*SL:251*/this.fillInStackTrace(); } public Throwable(final String detailMessage) { /*SL:211*/this.stackTrace = Throwable.UNASSIGNED_STACK; /*SL:228*/this.suppressedExceptions = Throwable.SUPPRESSED_SENTINEL; /*SL:266*/this.fillInStackTrace(); /*SL:267*/this.detailMessage = detailMessage; } public Throwable(final String detailMessage, final Throwable cause) { /*SL:211*/this.stackTrace = Throwable.UNASSIGNED_STACK; /*SL:228*/this.suppressedExceptions = Throwable.SUPPRESSED_SENTINEL; /*SL:288*/this.fillInStackTrace(); /*SL:289*/this.detailMessage = detailMessage; /*SL:290*/this.cause = cause; } public Throwable(final Throwable cause) { /*SL:211*/this.stackTrace = Throwable.UNASSIGNED_STACK; /*SL:228*/this.suppressedExceptions = Throwable.SUPPRESSED_SENTINEL; /*SL:311*/this.fillInStackTrace(); /*SL:312*/this.detailMessage = ((cause == null) ? null : cause.toString()); /*SL:313*/this.cause = cause; } protected Throwable(final String detailMessage, final Throwable cause, final boolean b, final boolean b2) { /*SL:211*/this.stackTrace = Throwable.UNASSIGNED_STACK; /*SL:228*/this.suppressedExceptions = Throwable.SUPPRESSED_SENTINEL; /*SL:360*/if (b2) { /*SL:361*/this.fillInStackTrace(); } else { /*SL:363*/this.stackTrace = null; } /*SL:365*/this.detailMessage = detailMessage; /*SL:366*/this.cause = cause; /*SL:367*/if (!b) { /*SL:368*/this.suppressedExceptions = null; } } private String detailMessage; /*SL:366*/private Throwable cause = this; public String getMessage() { /*SL:378*/return this.detailMessage; } public String getLocalizedMessage() { /*SL:392*/return this.getMessage(); } public synchronized Throwable getCause() { /*SL:416*/return (this.cause == this) ? null : this.cause; } public synchronized Throwable initCause(final Throwable cause) { /*SL:456*/if (this.cause != this) { /*SL:457*/throw new IllegalStateException(\"Can't overwrite cause with \" + /*EL:458*/Objects.toString(cause, \"a null\"), this); } /*SL:459*/if (cause == this) { /*SL:460*/throw new IllegalArgumentException(\"Self-causation not permitted\", this); } /*SL:461*/this.cause = cause; /*SL:462*/return this; } @Override public String toString() { final String name = /*EL:480*/this.getClass().getName(); final String localizedMessage = /*EL:481*/this.getLocalizedMessage(); /*SL:482*/return (localizedMessage != null) ? (name + \": \" + localizedMessage) : name; } public void printStackTrace() { /*SL:635*/this.printStackTrace(System.err); } public void printStackTrace(final PrintStream printStream) { /*SL:644*/this.printStackTrace(new WrappedPrintStream(printStream)); } private void printStackTrace(final PrintStreamOrWriter printStreamOrWriter) { final Set<Throwable> setFromMap = /*EL:651*/Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>()); /*SL:652*/setFromMap.add(this); /*SL:654*/synchronized (printStreamOrWriter.lock()) { /*SL:656*/printStreamOrWriter.println(this); final StackTraceElement[] ourStackTrace; final StackTraceElement[] array = /*EL:658*/ourStackTrace = this.getOurStackTrace(); for (int length = ourStackTrace.length, i = 0; i < length; ++i) { /*SL:659*/printStreamOrWriter.println(\"\\tat \" + ourStackTrace[i]); } final Throwable[] suppressed = /*EL:662*/this.getSuppressed(); for (int length2 = suppressed.length, j = 0; j < length2; ++j) { suppressed[j].printEnclosedStackTrace(/*EL:663*/printStreamOrWriter, array, \"Suppressed: \", \"\\t\", setFromMap); } final Throwable cause = /*EL:666*/this.getCause(); /*SL:667*/if (cause != null) { /*SL:668*/cause.printEnclosedStackTrace(printStreamOrWriter, array, \"Caused by: \", \"\", setFromMap); } } } private void printEnclosedStackTrace(final PrintStreamOrWriter printStreamOrWriter, final StackTraceElement[] array, final String s, final String s2, final Set<Throwable> set) { /*SL:681*/assert Thread.holdsLock(printStreamOrWriter.lock()); /*SL:682*/if (set.contains(this)) { /*SL:683*/printStreamOrWriter.println(\"\\t[CIRCULAR REFERENCE:\" + this + \"]\"); } else { /*SL:685*/set.add(this); final StackTraceElement[] ourStackTrace = /*EL:687*/this.getOurStackTrace(); int n = /*EL:688*/ourStackTrace.length - 1; /*SL:690*/for (int n2 = array.length - 1; n >= 0 && n2 >= 0 && ourStackTrace[n].equals(array[n2]); /*SL:691*/--n, --n2) {} final int n3 = /*EL:693*/ourStackTrace.length - 1 - n; /*SL:696*/printStreamOrWriter.println(s2 + s + this); /*SL:697*/for (int i = 0; i <= n; ++i) { /*SL:698*/printStreamOrWriter.println(s2 + \"\\tat \" + ourStackTrace[i]); } /*SL:699*/if (n3 != 0) { /*SL:700*/printStreamOrWriter.println(s2 + \"\\t... \" + n3 + \" more\"); } final Throwable[] suppressed = /*EL:703*/this.getSuppressed(); for (int length = suppressed.length, j = 0; j < length; ++j) { suppressed[j].printEnclosedStackTrace(/*EL:704*/printStreamOrWriter, ourStackTrace, \"Suppressed: \", s2 + \"\\t\", set); } final Throwable cause = /*EL:708*/this.getCause(); /*SL:709*/if (cause != null) { /*SL:710*/cause.printEnclosedStackTrace(printStreamOrWriter, ourStackTrace, \"Caused by: \", s2, set); } } } public void printStackTrace(final PrintWriter printWriter) { /*SL:722*/this.printStackTrace(new WrappedPrintWriter(printWriter)); } private static class WrappedPrintStream extends PrintStreamOrWriter { /*SL:741*/private final PrintStream printStream = printStream; WrappedPrintStream(final PrintStream printStream) { } @Override Object lock() { /*SL:745*/return this.printStream; } @Override void println(final Object o) { /*SL:749*/this.printStream.println(o); } } private static class WrappedPrintWriter extends PrintStreamOrWriter { /*SL:757*/private final PrintWriter printWriter = printWriter; WrappedPrintWriter(final PrintWriter printWriter) { } @Override Object lock() { /*SL:761*/return this.printWriter; } @Override void println(final Object o) { /*SL:765*/this.printWriter.println(o); } } public synchronized Throwable fillInStackTrace() { /*SL:782*/if (this.stackTrace != null || this.backtrace != null) { /*SL:784*/this.fillInStackTrace(0); /*SL:785*/this.stackTrace = Throwable.UNASSIGNED_STACK; } /*SL:787*/return this; } public StackTraceElement[] getStackTrace() { /*SL:817*/return this.getOurStackTrace().clone(); } private synchronized StackTraceElement[] getOurStackTrace() { /*SL:823*/if (this.stackTrace == Throwable.UNASSIGNED_STACK || (this.stackTrace == null && this.backtrace != null)) { final int stackTraceDepth = /*EL:825*/this.getStackTraceDepth(); /*SL:826*/this.stackTrace = new StackTraceElement[stackTraceDepth]; /*SL:827*/for (int i = 0; i < stackTraceDepth; ++i) { /*SL:828*/this.stackTrace[i] = this.getStackTraceElement(i); } } else/*SL:829*/ if (this.stackTrace == null) { /*SL:830*/return Throwable.UNASSIGNED_STACK; } /*SL:832*/return this.stackTrace; } public void setStackTrace(final StackTraceElement[] array) { final StackTraceElement[] stackTrace = /*EL:865*/array.clone(); /*SL:866*/for (int i = 0; i < stackTrace.length; ++i) { /*SL:867*/if (stackTrace[i] == null) { /*SL:868*/throw new NullPointerException(\"stackTrace[\" + i + \"]\"); } } /*SL:871*/synchronized (this) { /*SL:872*/if (this.stackTrace == null && this.backtrace == null) { /*SL:874*/return; } /*SL:875*/this.stackTrace = stackTrace; } } private void readObject(final ObjectInputStream objectInputStream) throws IOException, ClassNotFoundException { /*SL:915*/objectInputStream.defaultReadObject(); final List<Throwable> suppressedExceptions = /*EL:920*/this.suppressedExceptions; /*SL:921*/this.suppressedExceptions = Throwable.SUPPRESSED_SENTINEL; final StackTraceElement[] stackTrace = /*EL:923*/this.stackTrace; /*SL:924*/this.stackTrace = Throwable.UNASSIGNED_STACK.clone(); /*SL:926*/if (suppressedExceptions != null) { final int validateSuppressedExceptionsList = /*EL:927*/this.validateSuppressedExceptionsList(suppressedExceptions); /*SL:928*/if (validateSuppressedExceptionsList > 0) { final ArrayList suppressedExceptions2 = /*EL:929*/new ArrayList(Math.min(100, validateSuppressedExceptionsList)); /*SL:931*/for (final Throwable t : suppressedExceptions) { /*SL:934*/if (t == null) { /*SL:935*/throw new NullPointerException(\"Cannot suppress a null exception.\"); } /*SL:936*/if (t == this) { /*SL:937*/throw new IllegalArgumentException(\"Self-suppression not permitted\"); } /*SL:938*/suppressedExceptions2.add((Object)t); } /*SL:942*/this.suppressedExceptions = (List<Throwable>)suppressedExceptions2; } } else { /*SL:945*/this.suppressedExceptions = null; } /*SL:957*/if (stackTrace != null) { final StackTraceElement[] stackTrace2 = /*EL:960*/stackTrace.clone(); /*SL:961*/if (stackTrace2.length >= 1) { /*SL:962*/if (stackTrace2.length == 1 && SentinelHolder.STACK_TRACE_ELEMENT_SENTINEL.equals(stackTrace2[0])) { /*SL:965*/this.stackTrace = null; } else { final StackTraceElement[] array = /*EL:967*/stackTrace2; for (int length = array.length, i = 0; i < length; ++i) { /*SL:968*/if (array[i] == null) { /*SL:969*/throw new NullPointerException(\"null StackTraceElement in serial stream.\"); } } /*SL:971*/this.stackTrace = stackTrace2; } } } } private int validateSuppressedExceptionsList(final List<Throwable> list) throws IOException { boolean b; try { /*SL:988*/b = (list.getClass().getClassLoader() == null); } catch (final SecurityException ex) { /*SL:990*/b = false; } /*SL:993*/if (!b) { /*SL:994*/throw new StreamCorruptedException(\"List implementation class was not loaded by bootstrap class loader.\"); } final int size = /*EL:997*/list.size(); /*SL:998*/if (size < 0) { /*SL:999*/throw new StreamCorruptedException(\"Negative list size reported.\"); } /*SL:1001*/return size; } private synchronized void writeObject(final ObjectOutputStream objectOutputStream) throws IOException { /*SL:1018*/this.getOurStackTrace(); final StackTraceElement[] stackTrace = /*EL:1020*/this.stackTrace; try { /*SL:1022*/if (this.stackTrace == null) { /*SL:1023*/this.stackTrace = SentinelHolder.STACK_TRACE_SENTINEL; } /*SL:1024*/objectOutputStream.defaultWriteObject(); } finally { /*SL:1026*/this.stackTrace = stackTrace; } } public final synchronized void addSuppressed(final Throwable t) { /*SL:1081*/if (t == this) { /*SL:1082*/throw new IllegalArgumentException(\"Self-suppression not permitted\", t); } /*SL:1084*/if (t == null) { /*SL:1085*/throw new NullPointerException(\"Cannot suppress a null exception.\"); } /*SL:1087*/if (this.suppressedExceptions == null) { /*SL:1088*/return; } /*SL:1090*/if (this.suppressedExceptions == Throwable.SUPPRESSED_SENTINEL) { /*SL:1091*/this.suppressedExceptions = new ArrayList<Throwable>(1); } /*SL:1093*/this.suppressedExceptions.add(t); } static { /*SL:1096*/EMPTY_THROWABLE_ARRAY = new Throwable[0]; } public final synchronized Throwable[] getSuppressed() { /*SL:1114*/if (this.suppressedExceptions == Throwable.SUPPRESSED_SENTINEL || this.suppressedExceptions == null) { /*SL:1116*/return Throwable.EMPTY_THROWABLE_ARRAY; } /*SL:1118*/return this.suppressedExceptions.toArray(Throwable.EMPTY_THROWABLE_ARRAY); } }"
    			);
    }

    private static DecompilerSettings lineNumberSettings() {
        DecompilerSettings lineNumberSettings = defaultSettings();
        lineNumberSettings.setShowDebugLineNumbers(true);
        return lineNumberSettings;
    }
}

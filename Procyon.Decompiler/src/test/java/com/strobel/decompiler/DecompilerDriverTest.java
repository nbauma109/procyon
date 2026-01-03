package com.strobel.decompiler;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class DecompilerDriverTest {

	private void assertEqualsIgnoreEOL(String expected, String actual) {
        assertEquals(expected.replaceAll("\\s*\r?\n", "\n"), actual.replaceAll("\\s*\r?\n", "\n"));
    }

    private String getResourceAsString(String path) throws IOException {
        return IOUtils.toString(getClass().getResource(path), UTF_8);
    }

	private String getFileAsString(File outputDir, String pathname) throws IOException {
		return FileUtils.readFileToString(new File(outputDir, pathname), UTF_8);
	}

	@Test
	public void testDecompileModule() throws Exception {
		File outputDir = new File("build/platform-1.0.0_decompiled");
		DecompilerDriver.main("src/test/resources/jar/platform-1.0.0.jar", "--suppress-banner", "-o", outputDir.getAbsolutePath());
		assertEqualsIgnoreEOL(getResourceAsString("/txt/platform-1.0.0/module-info.txt"), getFileAsString(outputDir, "module-info.java"));
	}

	@Test
	public void testDecompileModuleByteCode() throws Exception {
		PrintStream systemOut = System.out;
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			System.setOut(new PrintStream(out, true, UTF_8.name()));
			DecompilerDriver.main("src/test/resources/jar/platform-1.0.0.jar", "--suppress-banner", "-r", "-cp", "-lv", "-ta", "-v");
			assertEqualsIgnoreEOL(getResourceAsString("/txt/platform-1.0.0/platform-1.0.0-bytecode.txt"), new String(out.toByteArray(), UTF_8));
		} finally {
			System.setOut(systemOut);
		}
	}
	
	@Test
	public void testDecompileAllJavaSyntaxes() throws Exception {
		File outputDir = new File("build/all-java-syntaxes");
		DecompilerDriver.main("src/test/resources/jar/all-java-syntaxes.jar", "--suppress-banner", "-o", outputDir.getAbsolutePath());
		assertEqualsIgnoreEOL(getResourceAsString("/txt/all-java-syntaxes/all-java-syntaxes.txt"), getFileAsString(outputDir, "demo/AllJavaSyntaxes.java"));
	}
	
	@Test
	public void testDecompileAllJavaSyntaxesByteCode() throws Exception {
		PrintStream systemOut = System.out;
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			System.setOut(new PrintStream(out, true, UTF_8.name()));
			DecompilerDriver.main("src/test/resources/jar/all-java-syntaxes.jar", "--suppress-banner", "-r", "-cp", "-lv", "-ta", "-v");
			assertEqualsIgnoreEOL(getResourceAsString("/txt/all-java-syntaxes/all-java-syntaxes-bytecode.txt"), new String(out.toByteArray(), UTF_8));
		} finally {
			System.setOut(systemOut);
		}
	}
	
}

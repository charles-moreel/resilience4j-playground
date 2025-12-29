/*
 * Customization TAO
 * Copyright (C) 2022-2025
 */
package org.example.resilience4jplayground;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

public class BenchmarkRunner {
    public static void main(final String[] args) throws Exception {

        final URLClassLoader classLoader = (URLClassLoader) BenchmarkRunner.class.getClassLoader();
        final StringBuilder classpath = new StringBuilder();
        for (final URL url : classLoader.getURLs()) {
            classpath.append(url.getPath()).append(File.pathSeparator);
        }
        System.setProperty("java.class.path", classpath.toString());

        org.openjdk.jmh.Main.main(args);
    }
}
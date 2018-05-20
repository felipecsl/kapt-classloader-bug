package com.felipecsl;

import javax.tools.ToolProvider;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

public class Sample {
  private static final String JVM_HOME = System.getenv("JAVA_HOME");
  private static final String COMPILER_CLASS = "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler";
  private static final String EXIT_CODE_CLASS = "org.jetbrains.kotlin.cli.common.ExitCode";
  private static final String PROJECT_PATH = System.getProperty("PROJECT_PATH");
  private static final String KOTLIN_HOME_PATH = PROJECT_PATH + "/kotlin_home/libexec/lib";
  private static final String KOTLIN_STDLIB_PATH = KOTLIN_HOME_PATH + "/kotlin-stdlib.jar";
  private static final String KOTLIN_REFLECT_PATH = KOTLIN_HOME_PATH + "/kotlin-reflect.jar";
  private static final String KOTLIN_SCRIPT_RUNTIME_PATH = KOTLIN_HOME_PATH + "/kotlin-script-runtime.jar";
  private static final String KOTLIN_COMPILER_PATH = KOTLIN_HOME_PATH + "/kotlin-compiler.jar";
  private static final String KOTLIN_APT_GRADLE = KOTLIN_HOME_PATH + "/kotlin-annotation-processing-gradle.jar";
  private static final String TOOLS_JAR_PATH = JVM_HOME + "/lib/tools.jar";
  private static final String OUTPUT_DIR = PROJECT_PATH + "/output";
  private static final String CLASSPATH =
      ":" + TOOLS_JAR_PATH
          + ":" + KOTLIN_COMPILER_PATH
          + ":" + KOTLIN_REFLECT_PATH
          + ":" + KOTLIN_STDLIB_PATH
          + ":" + KOTLIN_SCRIPT_RUNTIME_PATH;
  private static final URL[] COMPILER_CLASSPATH = new URL[4];

  private void runKapt() throws Exception {
    try {
      COMPILER_CLASSPATH[0] = new URL("file://" + KOTLIN_STDLIB_PATH);
      COMPILER_CLASSPATH[1] = new URL("file://" + KOTLIN_REFLECT_PATH);
      COMPILER_CLASSPATH[2] = new URL("file://" + KOTLIN_SCRIPT_RUNTIME_PATH);
      COMPILER_CLASSPATH[3] = new URL("file://" + KOTLIN_COMPILER_PATH);
    } catch (MalformedURLException e) {
      throw new AssertionError(e);
    }
    Object compilerShim = loadCompilerShim();
    Method execMethod = compilerShim.getClass().getMethod("exec", PrintStream.class, String[].class);
    Class<?> exitCodeClass = compilerShim.getClass().getClassLoader().loadClass(EXIT_CODE_CLASS);
    Method getCode = exitCodeClass.getMethod("getCode");
    String[] args = {
        "-d", OUTPUT_DIR,
        "-module-name",
        "kapt-tools-classloader-bug",
        "-include-runtime",
        "-no-reflect",
        "-verbose",
        "-classpath",
        CLASSPATH,
        "-Xadd-compiler-builtins",
        "-Xload-builtins-from-dependencies",
        "-P",
        "plugin:org.jetbrains.kotlin.kapt3:aptMode=apt,"
            + "plugin:org.jetbrains.kotlin.kapt3:apclasspath=" + KOTLIN_APT_GRADLE + ","
            + "plugin:org.jetbrains.kotlin.kapt3:apclasspath=" + PROJECT_PATH + "/test_ap/build/libs/test_ap-1.0-SNAPSHOT.jar,"
            + "plugin:org.jetbrains.kotlin.kapt3:useLightAnalysis=true,"
            + "plugin:org.jetbrains.kotlin.kapt3:correctErrorTypes=false,"
            + "plugin:org.jetbrains.kotlin.kapt3:verbose=true,"
            + "plugin:org.jetbrains.kotlin.kapt3:sources=" + OUTPUT_DIR + "/sources,"
            + "plugin:org.jetbrains.kotlin.kapt3:classes=" + OUTPUT_DIR + "/classes,"
            + "plugin:org.jetbrains.kotlin.kapt3:stubs=" + OUTPUT_DIR + "/stubs,"
            + "plugin:org.jetbrains.kotlin.kapt3:incrementalData=" + OUTPUT_DIR + "/incrementalData",
        "-Werror",
        "-Xplugin=" + KOTLIN_APT_GRADLE,
        PROJECT_PATH + "/inputs"
    };
    Object exitCodeMethod = execMethod.invoke(compilerShim, System.out, args);
    int exitCode = (Integer) getCode.invoke(exitCodeMethod);
    System.out.println("\nExit code=" + exitCode);
  }

  private Object loadCompilerShim() throws Exception {
    ClassLoader parentClassLoader = ToolProvider.getSystemToolClassLoader();
    URLClassLoader classLoader = new URLClassLoader(COMPILER_CLASSPATH, parentClassLoader);
    return classLoader.loadClass(COMPILER_CLASS).newInstance();
  }

  public static void main(String[] args) throws Exception {
    new Sample().runKapt();
  }
}
# Kotlin kapt Classloader bug

This repository illustrates a POC for a bug with the Kotlin kapt compiler as of version 1.2.40

### Reproducing the bug

1. Make sure you have a `JAVA_HOME` env var pointing to your JDK8 home directory
2. Run `./gradlew sample:runShadow`

You should see the following stack trace from Kapt on stdout:

```
error: [kapt] An exception occurred: java.lang.AssertionError: java.lang.ClassCastException: com.sun.tools.javac.api.JavacTrees cannot be cast to com.sun.source.util.Trees
        at com.sun.source.util.Trees.getJavacTrees(Trees.java:88)
        at com.sun.source.util.Trees.instance(Trees.java:77)
        at com.felipecsl.TestProcessor.init(TestProcessor.java:17)
        at org.jetbrains.kotlin.kapt3.ProcessorWrapper.init(annotationProcessing.kt)
        at com.sun.tools.javac.processing.JavacProcessingEnvironment$ProcessorState.<init>(JavacProcessingEnvironment.java:500)
        at com.sun.tools.javac.processing.JavacProcessingEnvironment$DiscoveredProcessors$ProcessorStateIterator.next(JavacProcessingEnvironment.java:597)
        at com.sun.tools.javac.processing.JavacProcessingEnvironment.discoverAndRunProcs(JavacProcessingEnvironment.java:690)
        at com.sun.tools.javac.processing.JavacProcessingEnvironment.access$1800(JavacProcessingEnvironment.java:91)
        at com.sun.tools.javac.processing.JavacProcessingEnvironment$Round.run(JavacProcessingEnvironment.java:1035)
        at com.sun.tools.javac.processing.JavacProcessingEnvironment.doProcessing(JavacProcessingEnvironment.java:1176)
        at com.sun.tools.javac.main.JavaCompiler.processAnnotations(JavaCompiler.java:1170)
        at com.sun.tools.javac.main.JavaCompiler.processAnnotations(JavaCompiler.java:1068)
        at org.jetbrains.kotlin.kapt3.AnnotationProcessingKt.doAnnotationProcessing(annotationProcessing.kt:87)
        at org.jetbrains.kotlin.kapt3.AnnotationProcessingKt.doAnnotationProcessing$default(annotationProcessing.kt:45)
        at org.jetbrains.kotlin.kapt3.AbstractKapt3Extension.runAnnotationProcessing(Kapt3Extension.kt:257)
        at org.jetbrains.kotlin.kapt3.AbstractKapt3Extension.analysisCompleted(Kapt3Extension.kt:212)
        at org.jetbrains.kotlin.kapt3.ClasspathBasedKapt3Extension.analysisCompleted(Kapt3Extension.kt:95)
        at org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM$analyzeFilesWithJavaIntegration$2.invoke(TopDownAnalyzerFacadeForJVM.kt:97)
        at org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(TopDownAnalyzerFacadeForJVM.kt:107)
        at org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration$default(TopDownAnalyzerFacadeForJVM.kt:84)
        at org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler$analyze$1.invoke(KotlinToJVMBytecodeCompiler.kt:374)
        at org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler$analyze$1.invoke(KotlinToJVMBytecodeCompiler.kt:64)
        at org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport.analyzeAndReport(AnalyzerWithCompilerReport.kt:101)
        at org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler.analyze(KotlinToJVMBytecodeCompiler.kt:365)
        at org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler.analyzeAndGenerate(KotlinToJVMBytecodeCompiler.kt:350)
        at org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler.compileBunchOfSources(KotlinToJVMBytecodeCompiler.kt:245)
        at org.jetbrains.kotlin.cli.jvm.K2JVMCompiler.doExecute(K2JVMCompiler.kt:207)
        at org.jetbrains.kotlin.cli.jvm.K2JVMCompiler.doExecute(K2JVMCompiler.kt:63)
        at org.jetbrains.kotlin.cli.common.CLICompiler.execImpl(CLICompiler.java:107)
        at org.jetbrains.kotlin.cli.common.CLICompiler.execImpl(CLICompiler.java:51)
        at org.jetbrains.kotlin.cli.common.CLITool.exec(CLITool.kt:96)
        at org.jetbrains.kotlin.cli.common.CLITool.exec(CLITool.kt:72)
        at org.jetbrains.kotlin.cli.common.CLITool.exec(CLITool.kt:38)
        at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
        at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        at java.lang.reflect.Method.invoke(Method.java:498)
        at com.felipecsl.Sample.runKapt(Sample.java:72)
        at com.felipecsl.Sample.main(Sample.java:84)
Caused by: java.lang.ClassCastException: com.sun.tools.javac.api.JavacTrees cannot be cast to com.sun.source.util.Trees
        at com.sun.source.util.Trees.getJavacTrees(Trees.java:86)
        ... 38 more
```

This bug was first surfaced when using Kapt with [Buck](https://buckbuild.com) as demonstrated on
this repository https://github.com/mmdango/okbuck_kapt_mwe.

### Root cause

This seems to only happen when running kapt in-process (as both this code and Buck do) on an annotation
processor that uses JDK APIs from `tools.jar`, eg.: `com.sun.source.util.Trees`. This can be reproduced
with several annotation processors that share this same characteristic, including 
[ButterKnife](https://github.com/jakewharton/butterknife), for example.

The `ClassCastException` happens because the kapt compiler itself already depends on some of the JDK
Tree APIs as you can see [here](https://github.com/JetBrains/kotlin/blob/b1d7935d4a1e40fbb0bfb029accd44e8d1398a18/plugins/kapt3/kapt3-compiler/src/org/jetbrains/kotlin/kapt3/annotationProcessing.kt#L25).
When first loaded, this causes some of these classes (including `JavacTrees`) to be loaded transitively by the [`ClassLoader` below](https://github.com/felipecsl/kapt-classloader-bug/blob/54c63939252b9e3d011bb5218f6a88f32c5b0be9/sample/src/main/java/com/felipecsl/Sample.java#L79-L83):
```
private Object loadCompilerShim() throws Exception {
  ClassLoader parentClassLoader = ToolProvider.getSystemToolClassLoader();
  URLClassLoader classLoader = new URLClassLoader(COMPILER_CLASSPATH, parentClassLoader);
  return classLoader.loadClass(COMPILER_CLASS).newInstance();
}
```

When `K2JVMCompiler` runs, it tries to load the annotation processors using a [brand new `ClassLoader`](https://github.com/JetBrains/kotlin/blob/b1d7935d4a1e40fbb0bfb029accd44e8d1398a18/plugins/kapt3/kapt3-compiler/src/org/jetbrains/kotlin/kapt3/Kapt3Extension.kt#L103-L105):

```
val classpath = annotationProcessingClasspath + compileClasspath
val classLoader = URLClassLoader(classpath.map { it.toURI().toURL() }.toTypedArray())
this.annotationProcessingClassLoader = classLoader
val processors = if (annotationProcessorFqNames.isNotEmpty()) {
    logger.info("Annotation processor class names are set, skip AP discovery")
    annotationProcessorFqNames.mapNotNull { tryLoadProcessor(it, classLoader) }
} else {
    logger.info("Need to discovery annotation processors in the AP classpath")
    ServiceLoader.load(Processor::class.java, classLoader).toList()
}
```

This means that `Trees` ends up being loaded again in `Trees#getJavacTrees` by the second classloader, as shown below:
```
static Trees getJavacTrees(Class<?> argType, Object arg) {
    try {
        ClassLoader cl = arg.getClass().getClassLoader();
        Class<?> c = Class.forName("com.sun.tools.javac.api.JavacTrees", false, cl);
        argType = Class.forName(argType.getName(), false, cl);
        Method m = c.getMethod("instance", new Class<?>[] { argType });
        return (Trees) m.invoke(null, new Object[] { arg });
    } catch (Throwable e) {
        throw new AssertionError(e);
    }
}
```

Which ends up causing the `ClassCastException` because [the same class cannot be loaded from multiple `ClassLoaders`](https://zeroturnaround.com/rebellabs/rebel-labs-tutorial-do-you-really-get-classloaders/4/).

This can be validated by running the jvm with `-verbose:class`:

```
...
[Loaded com.sun.source.util.Trees from file:/Library/Java/JavaVirtualMachines/jdk1.8.0_151.jdk/Contents/Home/lib/tools.jar]
[Loaded com.sun.source.util.Trees from file:/Library/Java/JavaVirtualMachines/jdk1.8.0_151.jdk/Contents/Home/lib/tools.jar]
...
```

You can see `com.sun.source.util.Trees` shows up twice, which confirms our theory.
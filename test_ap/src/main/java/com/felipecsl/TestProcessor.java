package com.felipecsl;

import com.sun.source.util.Trees;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import java.util.Collections;
import java.util.Set;

public class TestProcessor extends AbstractProcessor {
  private Trees trees;

  @Override public synchronized void init(ProcessingEnvironment env) {
    super.init(env);
    trees = Trees.instance(processingEnv);
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return Collections.singleton(TestAnnotation.class.getName());
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    throw new RuntimeException("boom");
//    System.out.println("Processing a file...");
//    return false;
  }
}

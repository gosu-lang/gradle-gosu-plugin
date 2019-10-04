/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gosulang.gradle.tasks.compile.incremental.asm;

import org.gosulang.gradle.tasks.compile.incremental.deps.ClassAnalysis;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.classanalysis.AsmConstants;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;

import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class ClassDependenciesVisitor extends ClassVisitor {

  private final static int API = AsmConstants.ASM_LEVEL;

  private final ClassDependenciesVisitor.MethodVisitor methodVisitor;
  private final ClassDependenciesVisitor.FieldVisitor fieldVisitor;
  private final Set<Integer> constants;
  private final Set<String> types;
  private final Predicate<String> typeFilter;
  private final StringInterner interner;
  private boolean isAnnotationType;
  private boolean dependencyToAll;
  private final ClassDependenciesVisitor.RetentionPolicyVisitor retentionPolicyVisitor;
  private final ClassDependenciesVisitor.AnnotationVisitor annotationVisitor;

  private ClassDependenciesVisitor(Predicate<String> typeFilter, ClassReader reader, StringInterner interner) {
    super(API);
    this.constants = new HashSet<>(2);
    this.types = new HashSet<>();
    this.methodVisitor = new ClassDependenciesVisitor.MethodVisitor();
    this.fieldVisitor = new ClassDependenciesVisitor.FieldVisitor();
    this.retentionPolicyVisitor = new ClassDependenciesVisitor.RetentionPolicyVisitor();
    this.annotationVisitor = new ClassDependenciesVisitor.AnnotationVisitor();
    this.typeFilter = typeFilter;
    this.interner = interner;
    collectClassDependencies(reader);
  }

  public static ClassAnalysis analyze(String className, ClassReader reader, StringInterner interner) {
    ClassDependenciesVisitor visitor = new ClassDependenciesVisitor(new ClassRelevancyFilter(className), reader, interner);
    reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return new ClassAnalysis(interner.intern(className), visitor.getClassDependencies(), visitor.isDependencyToAll(), visitor.getConstants());
  }

  @Override
  public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
    isAnnotationType = isAnnotationType(interfaces);
    if (superName != null) {
      // superName can be null if what we are analyzing is `java.lang.Object`
      // which can happen when a custom Java SDK is on classpath (typically, android.jar)
      String type = typeOfFromSlashyString(superName);
      maybeAddDependentType(type);
    }
    for (String s : interfaces) {
      String interfaceType = typeOfFromSlashyString(s);
      maybeAddDependentType(interfaceType);
    }

  }

// this would be for Java 11 but we are compiling with 8
//  @Override
//  public ModuleVisitor visitModule(String name, int access, String version) {
//    dependencyToAll = true;
//    return null;
//  }

  // performs a fast analysis of classes referenced in bytecode (method bodies)
  // avoiding us to implement a costly visitor and potentially missing edge cases
  private void collectClassDependencies(ClassReader reader) {
    char[] charBuffer = new char[reader.getMaxStringLength()];
    for (int i = 1; i < reader.getItemCount(); i++) {
      int itemOffset = reader.getItem(i);
      if (itemOffset > 0 && reader.readByte(itemOffset - 1) == 7) {
        // A CONSTANT_Class entry, read the class descriptor
        String classDescriptor = reader.readUTF8(itemOffset, charBuffer);
        Type type = Type.getObjectType(classDescriptor);
        while (type.getSort() == Type.ARRAY) {
          type = type.getElementType();
        }
        if (type.getSort() != Type.OBJECT) {
          // A primitive type
          continue;
        }
        String name = type.getClassName();
        maybeAddDependentType(name);
      }
    }
  }

  protected void maybeAddDependentType(String type) {
    if (typeFilter.test(type)) {
      types.add(intern(type));
    }
  }

  private String intern(String type) {
    return interner.intern(type);
  }

  protected String typeOfFromSlashyString(String slashyStyleDesc) {
    return Type.getObjectType(slashyStyleDesc).getClassName();
  }

  public Set<String> getClassDependencies() {
    return types;
  }

  public Set<Integer> getConstants() {
    return constants;
  }

  private boolean isAnnotationType(String[] interfaces) {
    return interfaces.length == 1 && interfaces[0].equals("java/lang/annotation/Annotation");
  }

  @Override
  public org.objectweb.asm.FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
    maybeAddDependentType(descTypeOf(desc));
    if (isAccessibleConstant(access, value)) {
      // we need to compute a hash for a constant, which is based on the name of the constant + its value
      // otherwise we miss the case where a class defines several constants with the same value, or when
      // two values are switched
      constants.add((name + '|' + value).hashCode()); //non-private const
    }
    return fieldVisitor;
  }

  private static boolean isAccessibleConstant(int access, Object value) {
    return isConstant(access) && !isPrivate(access) && value != null;
  }

  protected String descTypeOf(String desc) {
    Type type = Type.getType(desc);
    if (type.getSort() == Type.ARRAY && type.getDimensions() > 0) {
      type = type.getElementType();
    }
    return type.getClassName();
  }

  @Override
  public org.objectweb.asm.MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
    Type methodType = Type.getMethodType(desc);
    maybeAddDependentType(methodType.getReturnType().getClassName());
    for (Type argType : methodType.getArgumentTypes()) {
      maybeAddDependentType(argType.getClassName());
    }
    return methodVisitor;
  }

  @Override
  public org.objectweb.asm.AnnotationVisitor visitAnnotation(String desc, boolean visible) {
    if (isAnnotationType && "Ljava/lang/annotation/Retention;".equals(desc)) {
      return retentionPolicyVisitor;
    } else {
      maybeAddDependentType(Type.getType(desc).getClassName());
      return annotationVisitor;
    }
  }

  private static boolean isPrivate(int access) {
    return (access & Opcodes.ACC_PRIVATE) != 0;
  }

  private static boolean isConstant(int access) {
    return (access & Opcodes.ACC_FINAL) != 0 && (access & Opcodes.ACC_STATIC) != 0;
  }

  public boolean isDependencyToAll() {
    return dependencyToAll;
  }

  private class FieldVisitor extends org.objectweb.asm.FieldVisitor {

    public FieldVisitor() {
      super(API);
    }

    @Override
    public org.objectweb.asm.AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      maybeAddDependentType(Type.getType(descriptor).getClassName());
      return annotationVisitor;
    }

    @Override
    public org.objectweb.asm.AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
      maybeAddDependentType(Type.getType(descriptor).getClassName());
      return annotationVisitor;
    }
  }

  private class MethodVisitor extends org.objectweb.asm.MethodVisitor {

    protected MethodVisitor() {
      super(API);
    }

    @Override
    public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
      maybeAddDependentType(descTypeOf(desc));
      super.visitLocalVariable(name, desc, signature, start, end, index);
    }

    @Override
    public org.objectweb.asm.AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
      maybeAddDependentType(Type.getType(descriptor).getClassName());
      return annotationVisitor;
    }

    @Override
    public org.objectweb.asm.AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
      maybeAddDependentType(Type.getType(descriptor).getClassName());
      return annotationVisitor;
    }

    @Override
    public org.objectweb.asm.AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
      maybeAddDependentType(Type.getType(descriptor).getClassName());
      return annotationVisitor;
    }
  }

  private class RetentionPolicyVisitor extends org.objectweb.asm.AnnotationVisitor {
    public RetentionPolicyVisitor() {
      super(ClassDependenciesVisitor.API);
    }

    @Override
    public void visitEnum(String name, String desc, String value) {
      if ("Ljava/lang/annotation/RetentionPolicy;".equals(desc)) {
        RetentionPolicy policy = RetentionPolicy.valueOf(value);
        if (policy == RetentionPolicy.SOURCE) {
          dependencyToAll = true;
        }
      }
    }
  }

  private class AnnotationVisitor extends org.objectweb.asm.AnnotationVisitor {
    public AnnotationVisitor() {
      super(ClassDependenciesVisitor.API);
    }

    @Override
    public void visit(String name, Object value) {
      if (value instanceof Type) {
        maybeAddDependentType(((Type) value).getClassName());
      }
    }

    @Override
    public org.objectweb.asm.AnnotationVisitor visitArray(String name) {
      return this;
    }

    @Override
    public org.objectweb.asm.AnnotationVisitor visitAnnotation(String name, String descriptor) {
      maybeAddDependentType(Type.getType(descriptor).getClassName());
      return this;
    }
  }
}

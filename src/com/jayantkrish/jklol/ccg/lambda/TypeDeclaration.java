package com.jayantkrish.jklol.ccg.lambda;

public interface TypeDeclaration {
  
  public static Type TOP = Type.createAtomic("⊤");
  public static Type BOTTOM = Type.createAtomic("⊥");

  public Type getType(String constant);
  
  public Type unify(Type t1, Type t2);
  
  public Type meet(Type t1, Type t2);
  
  public Type join(Type t1, Type t2);
  
  public boolean isAtomicSubtype(String subtype, String supertype);
}

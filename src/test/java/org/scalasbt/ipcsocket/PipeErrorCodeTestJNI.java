package org.scalasbt.ipcsocket;

public class PipeErrorCodeTestJNI extends PipeErrorCodeTest {
  @Override
  boolean useJNI() {
    return true;
  }
}

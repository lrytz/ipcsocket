package org.scalasbt.ipcsocket;

public class PipeSquattingTestJNI extends PipeSquattingTest {
  @Override
  boolean useJNI() {
    return true;
  }
}

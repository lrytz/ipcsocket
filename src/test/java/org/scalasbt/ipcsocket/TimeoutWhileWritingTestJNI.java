package org.scalasbt.ipcsocket;

public class TimeoutWhileWritingTestJNI extends TimeoutWhileWritingTest {
  @Override
  boolean useJNI() {
    return true;
  }
}

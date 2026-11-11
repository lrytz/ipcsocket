package org.scalasbt.ipcsocket;

public class ReadTimeoutTestJNI extends ReadTimeoutTest {
  @Override
  boolean useJNI() {
    return true;
  }
}

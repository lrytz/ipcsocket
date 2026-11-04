package org.scalasbt.ipcsocket;

public class AcceptCloseTestJNI extends AcceptCloseTest {
  @Override
  boolean useJNI() {
    return true;
  }
}

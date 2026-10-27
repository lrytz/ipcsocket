package org.scalasbt.ipcsocket;

public class ClientCloseTestJNI extends ClientCloseTest {
  @Override
  boolean useJNI() {
    return true;
  }
}

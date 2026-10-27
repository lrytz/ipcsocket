package org.scalasbt.ipcsocket;

public class PeerCloseTestJNI extends PeerCloseTest {
  @Override
  boolean useJNI() {
    return true;
  }
}

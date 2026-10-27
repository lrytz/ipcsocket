package org.scalasbt.ipcsocket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import org.junit.Test;
import static org.junit.Assert.*;

public class ServerSocketStateTest extends BaseSocketSetup {
  @Test
  public void reportsBoundAndClosed() throws IOException, InterruptedException {
    withSocket(
        sock -> {
          ServerSocket serverSocket = newServerSocket(sock);
          assertTrue(serverSocket.isBound());
          assertFalse(serverSocket.isClosed());
          serverSocket.close();
          serverSocket.close();
          assertTrue(serverSocket.isClosed());
        });
  }

  @Test
  public void channelBindsUnixAddress() throws IOException, InterruptedException {
    if (isWin) return;
    withSocket(
        sock -> {
          UnixDomainServerSocket serverSocket = new UnixDomainServerSocket(50, null, useJNI());
          try (ServerSocketChannel channel = ServerSocketChannels.fromServerSocket(serverSocket)) {
            channel.bind(new UnixDomainServerSocket.UnixDomainServerSocketAddress(sock));
            assertTrue(serverSocket.isBound());
            newClientSocket(sock).close();
          }
        });
  }

  @Test
  public void channelDoesNotBindTcp() throws IOException {
    if (isWin) return;
    UnixDomainServerSocket serverSocket = new UnixDomainServerSocket(50, null, useJNI());
    try (ServerSocketChannel channel = ServerSocketChannels.fromServerSocket(serverSocket)) {
      try {
        channel.bind(null);
      } catch (IllegalArgumentException e) {
      }
      assertNull(serverSocket.getInetAddress());
    }
  }
}

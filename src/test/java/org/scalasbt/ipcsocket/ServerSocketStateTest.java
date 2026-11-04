package org.scalasbt.ipcsocket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
  public void acceptAfterCloseThrowsSocketException() throws IOException, InterruptedException {
    withSocket(
        sock -> {
          ServerSocket serverSocket = newServerSocket(sock);
          serverSocket.close();
          try {
            serverSocket.accept();
            fail("accept succeeded after close");
          } catch (SocketException e) {
          }
        });
  }

  @Test
  public void hasNoTcpAddress() throws IOException, InterruptedException {
    withSocket(
        sock -> {
          try (ServerSocket serverSocket = newServerSocket(sock)) {
            assertEquals(-1, serverSocket.getLocalPort());
            assertNull(serverSocket.getInetAddress());
            assertNull(serverSocket.getLocalSocketAddress());
          }
        });
  }

  @Test(timeout = 10000)
  public void closeDuringAcceptThrowsSocketException() throws Exception {
    if (isWin) return;
    withSocket(
        sock -> {
          ServerSocket serverSocket = newServerSocket(sock);
          CompletableFuture<Socket> accept =
              CompletableFuture.supplyAsync(
                  () -> {
                    try {
                      return serverSocket.accept();
                    } catch (IOException e) {
                      throw new CompletionException(e);
                    }
                  });
          Thread.sleep(500);
          serverSocket.close();
          try {
            accept.get(5, TimeUnit.SECONDS).close();
            fail("accept returned a socket after close");
          } catch (ExecutionException e) {
            assertTrue(e.getCause().toString(), e.getCause() instanceof SocketException);
          } catch (InterruptedException | TimeoutException e) {
            throw new IOException(e);
          }
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

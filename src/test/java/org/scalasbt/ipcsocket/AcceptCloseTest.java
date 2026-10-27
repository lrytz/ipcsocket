package org.scalasbt.ipcsocket;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import static org.junit.Assert.*;

/** Closing a server is how a thread blocked in accept() is stopped. */
public class AcceptCloseTest extends BaseSocketSetup {
  boolean supported() {
    return isWin;
  }

  @Test(timeout = 10000)
  public void closeStopsAccept() throws Exception {
    if (!supported()) return;
    withSocket(
        sock -> {
          ServerSocket serverSocket = newServerSocket(sock);
          CompletableFuture<Void> accept =
              CompletableFuture.runAsync(
                  () -> {
                    try {
                      serverSocket.accept().close();
                    } catch (IOException e) {
                      throw new RuntimeException(e);
                    }
                  });
          Thread.sleep(500);
          serverSocket.close();
          try {
            accept.get(5, TimeUnit.SECONDS);
            fail("accept returned a socket after close");
          } catch (ExecutionException e) {
            assertTrue(e.getCause().getCause() instanceof IOException);
          } catch (java.util.concurrent.TimeoutException e) {
            fail("close did not stop accept");
          }
        });
  }
}

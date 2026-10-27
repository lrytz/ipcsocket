package org.scalasbt.ipcsocket;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * If another principal creates the pipe name first, the server must not add its instances to that
 * pipe: clients could then connect to the other principal's instance.
 */
public class PipeSquattingTest extends BaseSocketSetup {
  @Test(timeout = 30000)
  public void acceptRefusesPipeCreatedByOthers() throws Exception {
    if (!isWin) return;
    withSocket(
        sock -> {
          Win32NamedPipeLibraryProvider p = Win32NamedPipeLibraryProvider.get(useJNI());
          Handle squatter =
              p.CreateNamedPipe(
                  sock,
                  p.PIPE_ACCESS_DUPLEX() | p.FILE_FLAG_OVERLAPPED(),
                  0,
                  Win32NamedPipeLibrary.PIPE_UNLIMITED_INSTANCES,
                  65535,
                  65535,
                  0,
                  p.FILE_ALL_ACCESS(),
                  Win32SecurityLevel.NO_SECURITY);
          ServerSocket server = null;
          try {
            server = newServerSocket(sock);
            ServerSocket s = server;
            CompletableFuture<Void> accept =
                CompletableFuture.runAsync(
                    () -> {
                      try {
                        s.accept().close();
                      } catch (IOException e) {
                        throw new RuntimeException(e);
                      }
                    });
            try {
              accept.get(5, TimeUnit.SECONDS);
              fail("accept returned a socket");
            } catch (ExecutionException e) {
              // expected: the pipe name belongs to someone else
            } catch (TimeoutException e) {
              fail("accept joined the existing pipe and waits for a client");
            }
          } catch (IOException e) {
            // also fine: refused when the server socket was created
          } finally {
            if (server != null) server.close();
            p.CloseHandle(squatter);
          }
        });
  }
}

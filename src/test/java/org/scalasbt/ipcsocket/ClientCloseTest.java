package org.scalasbt.ipcsocket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClientCloseTest extends BaseSocketSetup {
  @Test(timeout = 10000)
  public void serverSeesClientClose() throws IOException, InterruptedException {
    withSocket(
        sock -> {
          ServerSocket serverSocket = newServerSocket(sock);
          try {
            Socket client = newClientSocket(sock);
            Socket peer = serverSocket.accept();
            client.close();
            client.close();
            assertTrue(client.isClosed());
            try {
              assertEquals(-1, peer.getInputStream().read());
            } catch (IOException e) {
              // a named pipe reports the closed client as a broken pipe
            } finally {
              peer.close();
            }
          } finally {
            serverSocket.close();
          }
        });
  }
}

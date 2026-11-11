package org.scalasbt.ipcsocket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import org.junit.Test;
import static org.junit.Assert.*;

/** A read must end, not hang, when the peer closes the connection. */
public class PeerCloseTest extends BaseSocketSetup {
  @Test(timeout = 10000)
  public void readEndsWhenPeerCloses() throws IOException, InterruptedException {
    withSocket(
        sock -> {
          ServerSocket serverSocket = newServerSocket(sock);
          SocketPair pair = SocketPair.connect(this, serverSocket, sock);
          try (Socket client = pair.client) {
            pair.peer.close();
            try {
              assertEquals(-1, client.getInputStream().read());
            } catch (IOException e) {
              // a named pipe reports the closed peer as a broken pipe
            }
          } finally {
            serverSocket.close();
          }
        });
  }
}

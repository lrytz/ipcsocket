package org.scalasbt.ipcsocket;

import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.*;

public class PipeErrorCodeTest extends BaseSocketSetup {
  @Test
  public void connectToMissingPipeReportsFileNotFound() throws Exception {
    if (!isWin) return;
    for (int i = 0; i < 1000; i++) {
      try {
        newClientSocket("\\\\.\\pipe\\ipcsocket-missing" + rand.nextInt()).close();
        fail("connected to a pipe that doesn't exist");
      } catch (IOException e) {
        String m = String.valueOf(e.getMessage());
        // ERROR_FILE_NOT_FOUND
        assertTrue(m, m.contains("(2)") || m.contains("error code 2)"));
      }
      if (i % 100 == 0) System.gc();
    }
  }
}

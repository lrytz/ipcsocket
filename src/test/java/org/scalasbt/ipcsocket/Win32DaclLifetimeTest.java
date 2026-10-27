package org.scalasbt.ipcsocket;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Advapi32;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase.SECURITY_ATTRIBUTES;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinNT.ACCESS_ALLOWED_ACE;
import com.sun.jna.platform.win32.WinNT.ACL;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinNT.HANDLEByReference;
import com.sun.jna.platform.win32.WinNT.SECURITY_DESCRIPTOR;
import com.sun.jna.platform.win32.WinNT.TOKEN_OWNER;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Test;

/**
 * Win32SecurityLibrary.createSecurityWithDacl stores only the address of the JNA-allocated ACL in
 * the security descriptor, and reads the owner SID from a buffer that is already unreachable. Once
 * the GC frees either, CreateNamedPipe sees an invalid DACL and fails with ERROR_INVALID_ACL (1336)
 * or ERROR_NOACCESS (998). sbt reports this as "Could not create lock for
 * \\.\pipe\sbt-load..._lock".
 */
public class Win32DaclLifetimeTest {
  interface SecurityApi extends Library {
    SecurityApi INSTANCE = Native.load("advapi32", SecurityApi.class);

    boolean IsValidSecurityDescriptor(Pointer sd);

    boolean GetSecurityDescriptorDacl(
        Pointer sd, IntByReference present, PointerByReference dacl, IntByReference defaulted);

    boolean IsValidAcl(Pointer acl);
  }

  static final int ACCESS_MASK = WinNT.FILE_GENERIC_READ;
  static final int GC_ROUNDS = 20;

  @Before
  public void onlyOnWindows() {
    assumeTrue(System.getProperty("os.name", "").toLowerCase().startsWith("win"));
  }

  /** The descriptor created by the library loses its DACL once the GC runs. */
  @Test
  public void daclSurvivesGc() {
    SECURITY_ATTRIBUTES sa =
        Win32SecurityLibrary.createSecurityWithDacl(ACCESS_MASK, Win32SecurityLevel.OWNER_DACL);
    checkAfterGc("library", sa);
  }

  /** Control: the same descriptor, with the ACL kept reachable, survives the GC. */
  @Test
  public void daclSurvivesGcWhenKeptReachable() {
    List<Object> keepAlive = new ArrayList<>();
    SECURITY_ATTRIBUTES sa = createSecurityWithOwnerDacl(ACCESS_MASK, keepAlive);
    checkAfterGc("control", sa);
    assertFalse(keepAlive.isEmpty());
  }

  /** The code path sbt's BootServerSocket takes, with GC and native allocations in parallel. */
  @Test
  public void serverSocketsUnderGcPressure() throws InterruptedException {
    final int iterations = Integer.getInteger("repro.iterations", 3000);
    final AtomicBoolean done = new AtomicBoolean(false);
    Thread pressure =
        new Thread(
            () -> {
              while (!done.get()) {
                System.gc();
                List<Memory> spray = new ArrayList<>();
                for (int i = 0; i < 2000; i++) {
                  Memory m = new Memory(8 + (i % 16) * 4);
                  m.setMemory(0, m.size(), (byte) 0xFF);
                  spray.add(m);
                }
              }
            },
            "gc-pressure");
    pressure.setDaemon(true);
    pressure.start();

    Map<String, Integer> failures = new TreeMap<>();
    long base = System.nanoTime();
    try {
      for (int i = 0; i < iterations; i++) {
        String name = "ipcsocket-dacl-repro-" + base + "-" + i;
        try {
          new Win32NamedPipeServerSocket(name, false, Win32SecurityLevel.OWNER_DACL).close();
        } catch (IOException e) {
          String msg = String.valueOf(e.getMessage()).replace(name, "<name>");
          failures.merge(msg, 1, Integer::sum);
          if (failures.values().stream().mapToInt(Integer::intValue).sum() <= 5)
            System.out.println("[pressure] iteration " + i + ": " + e);
        }
      }
    } finally {
      done.set(true);
      pressure.join();
    }
    System.out.println("[pressure] " + iterations + " server sockets, failures: " + failures);
    assertTrue("failures: " + failures, failures.isEmpty());
  }

  /**
   * Runs GC until JNA's cleaner frees unreachable native memory, fills freed blocks of the ACL's
   * size with 0xFF, then checks that the DACL is intact and CreateNamedPipe accepts the attributes.
   */
  static void checkAfterGc(String label, SECURITY_ATTRIBUTES sa) {
    Pointer sd = sa.lpSecurityDescriptor;
    Pointer acl = dacl(label, sd);
    int aclSize = acl.getShort(2) & 0xFFFF;
    System.out.println("[" + label + "] before GC: " + describe(sd, acl));

    List<Memory> spray = new ArrayList<>();
    int round = 0;
    while (round < GC_ROUNDS && SecurityApi.INSTANCE.IsValidAcl(acl)) {
      round++;
      System.gc();
      sleep(100);
      for (int i = 0; i < 5000; i++) {
        Memory m = new Memory(aclSize);
        m.setMemory(0, aclSize, (byte) 0xFF);
        spray.add(m);
      }
    }
    System.out.println(
        "[" + label + "] after " + round + " GC rounds: " + describe(sd, dacl(label, sd)));

    String name = "\\\\.\\pipe\\ipcsocket-dacl-repro-" + label + "-" + System.nanoTime();
    HANDLE h =
        Win32NamedPipeLibrary.INSTANCE.CreateNamedPipe(
            name,
            Win32NamedPipeLibrary.FILE_FLAG_FIRST_PIPE_INSTANCE
                | Win32NamedPipeLibrary.PIPE_ACCESS_DUPLEX,
            0,
            1,
            1024,
            1024,
            0,
            sa);
    int error = Native.getLastError();
    boolean created = !Win32NamedPipeLibrary.INVALID_HANDLE_VALUE.equals(h);
    if (created) Kernel32.INSTANCE.CloseHandle(h);
    System.out.println(
        "[" + label + "] CreateNamedPipe " + (created ? "succeeded" : "failed, error " + error));
    assertTrue("[" + label + "] DACL is invalid after GC", SecurityApi.INSTANCE.IsValidAcl(acl));
    assertTrue("[" + label + "] CreateNamedPipe failed with error " + error, created);
  }

  static Pointer dacl(String label, Pointer sd) {
    IntByReference present = new IntByReference();
    PointerByReference dacl = new PointerByReference();
    IntByReference defaulted = new IntByReference();
    assertTrue(
        "[" + label + "] GetSecurityDescriptorDacl failed, error " + Native.getLastError(),
        SecurityApi.INSTANCE.GetSecurityDescriptorDacl(sd, present, dacl, defaulted));
    assertEquals("[" + label + "] DACL present", 1, present.getValue());
    return dacl.getValue();
  }

  static String describe(Pointer sd, Pointer acl) {
    StringBuilder bytes = new StringBuilder();
    for (byte b : acl.getByteArray(0, 8)) bytes.append(String.format("%02x ", b));
    return String.format(
        "sd valid=%b, acl@%s valid=%b, header=[%s] (revision, sbz1, size, aceCount, sbz2)",
        SecurityApi.INSTANCE.IsValidSecurityDescriptor(sd),
        acl,
        SecurityApi.INSTANCE.IsValidAcl(acl),
        bytes.toString().trim());
  }

  /** Win32SecurityLibrary.createSecurityWithDacl for OWNER_DACL, keeping native buffers alive. */
  static SECURITY_ATTRIBUTES createSecurityWithOwnerDacl(int accessMask, List<Object> keepAlive) {
    SECURITY_DESCRIPTOR sd = new SECURITY_DESCRIPTOR(64 * 1024);
    assertTrue(
        Advapi32.INSTANCE.InitializeSecurityDescriptor(sd, WinNT.SECURITY_DESCRIPTOR_REVISION));

    HANDLEByReference token = new HANDLEByReference();
    assertTrue(
        Advapi32.INSTANCE.OpenProcessToken(
            Kernel32.INSTANCE.GetCurrentProcess(), WinNT.TOKEN_QUERY, token));
    IntByReference length = new IntByReference();
    Advapi32.INSTANCE.GetTokenInformation(
        token.getValue(), WinNT.TOKEN_INFORMATION_CLASS.TokenOwner, null, 0, length);
    TOKEN_OWNER owner = new TOKEN_OWNER(length.getValue());
    assertTrue(
        Advapi32.INSTANCE.GetTokenInformation(
            token.getValue(),
            WinNT.TOKEN_INFORMATION_CLASS.TokenOwner,
            owner,
            length.getValue(),
            length));
    Kernel32.INSTANCE.CloseHandle(token.getValue());

    int sidLength = Advapi32.INSTANCE.GetLengthSid(owner.Owner);
    int cbAcl =
        Native.getNativeSize(ACL.class, null)
            + Native.getNativeSize(ACCESS_ALLOWED_ACE.class, null)
            + sidLength
            - DWORD.SIZE;
    cbAcl = Advapi32Util.alignOnDWORD(cbAcl);
    ACL acl = new ACL(cbAcl);
    assertTrue(Advapi32.INSTANCE.InitializeAcl(acl, cbAcl, WinNT.ACL_REVISION));
    assertTrue(
        Advapi32.INSTANCE.AddAccessAllowedAce(acl, WinNT.ACL_REVISION, accessMask, owner.Owner));
    assertTrue(Advapi32.INSTANCE.SetSecurityDescriptorDacl(sd, true, acl, false));
    keepAlive.add(owner);
    keepAlive.add(acl);

    SECURITY_ATTRIBUTES sa = new SECURITY_ATTRIBUTES();
    sa.dwLength = new DWORD(sd.size());
    sa.lpSecurityDescriptor = sd.getPointer();
    sa.bInheritHandle = false;
    return sa;
  }

  static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}

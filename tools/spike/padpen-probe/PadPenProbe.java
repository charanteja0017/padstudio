import android.system.Os;
import android.system.OsConstants;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Plan Step 9 spike: can a process we start ourselves (shell uid, over wireless debugging) take the
 * Focus Pen Pro's keys before HyperOS does — no root, no Key Mapper, no NDK?
 *
 * Run on the tablet:
 *   app_process -Djava.class.path=/data/local/tmp/padpen-probe.dex /system/bin PadPenProbe [--inject]
 *
 * 1. Finds the pen's key device by name in /proc/bus/input/devices ("Xiaomi Focus Pen Pro Keyboard").
 * 2. Opens /dev/input/eventN and takes an exclusive grab with EVIOCGRAB through the platform's own
 *    (hidden) Os.ioctlInt, which passes a non-null pointer: evdev treats any non-null argument as "grab".
 *    While grabbed, nothing else (HyperOS included) receives the device's events.
 * 3. Logs each gesture: F19 = squeeze, KP9 = slide up, KP3 = slide down, KPENTER = double tap.
 * 4. With --inject, a double tap injects VOLUME_DOWN then VOLUME_UP (net zero) to prove injection.
 * 5. On a reconnect (read fails or the node disappears) it waits for the pen and grabs it again.
 * The grab ends when the process exits (the kernel releases it with the file descriptor).
 */
public final class PadPenProbe {
    private static final String PEN = "Xiaomi Focus Pen Pro Keyboard";
    private static final int EVIOCGRAB = 0x40044590; // _IOW('E', 0x90, int)
    private static final int EV_KEY = 1;
    private static final int KEY_KP9 = 73, KEY_KP3 = 81, KEY_KPENTER = 96, KEY_F19 = 189;

    public static void main(String[] args) throws Exception {
        boolean inject = args.length > 0 && args[0].equals("--inject");
        log("start uid=" + Os.getuid() + " inject=" + inject);
        Method ioctlInt = findIoctlInt();
        log("ioctlInt: " + (ioctlInt == null ? "NOT FOUND" : ioctlInt.toGenericString()));
        while (true) {
            String node = findNode();
            if (node == null) {
                log("waiting: '" + PEN + "' not present");
                Thread.sleep(500);
                continue;
            }
            FileDescriptor fd;
            try {
                fd = Os.open(node, OsConstants.O_RDONLY, 0);
            } catch (Exception e) {
                log("open " + node + " failed: " + e);
                Thread.sleep(1000);
                continue;
            }
            boolean grabbed = false;
            if (ioctlInt != null) {
                try {
                    ioctlInt.invoke(null, fd, EVIOCGRAB);
                    grabbed = true;
                } catch (java.lang.reflect.InvocationTargetException e) {
                    log("EVIOCGRAB failed: " + e.getCause()); // EBUSY = someone else (Key Mapper) holds it
                }
            }
            log("opened " + node + " grabbed=" + grabbed);
            readLoop(fd, inject);
            try { Os.close(fd); } catch (Exception ignored) { }
            log("device gone; waiting for reconnect");
            Thread.sleep(300);
        }
    }

    private static void readLoop(FileDescriptor fd, boolean inject) {
        byte[] buf = new byte[24 * 64];
        while (true) {
            int n;
            try {
                n = Os.read(fd, buf, 0, buf.length);
            } catch (Exception e) {
                log("read ended: " + e);
                return;
            }
            if (n <= 0) return;
            ByteBuffer bb = ByteBuffer.wrap(buf, 0, n).order(ByteOrder.LITTLE_ENDIAN);
            for (int off = 0; off + 24 <= n; off += 24) {
                int type = bb.getShort(off + 16) & 0xffff;
                int code = bb.getShort(off + 18) & 0xffff;
                int value = bb.getInt(off + 20);
                if (type != EV_KEY || value == 2) continue; // key events only, no autorepeat
                String name = code == KEY_F19 ? "SQUEEZE" : code == KEY_KP9 ? "SLIDE_UP" : code == KEY_KP3 ? "SLIDE_DOWN"
                        : code == KEY_KPENTER ? "DOUBLE_TAP" : ("KEY_" + code);
                log(name + (value == 1 ? " down" : " up"));
                if (inject && value == 1 && code == KEY_KPENTER) {
                    exec("input keyevent 25"); // VOLUME_DOWN
                    exec("input keyevent 24"); // VOLUME_UP: net zero
                    log("injected VOLUME_DOWN + VOLUME_UP");
                }
            }
        }
    }

    /** The platform's own ioctl wrapper: Os.ioctlInt(FileDescriptor, int) on Android 12+ (hidden API). */
    private static Method findIoctlInt() {
        for (String cls : new String[] {"android.system.Os", "libcore.io.Linux"}) {
            try {
                Method m = Class.forName(cls).getDeclaredMethod("ioctlInt", FileDescriptor.class, int.class);
                m.setAccessible(true);
                if (java.lang.reflect.Modifier.isStatic(m.getModifiers())) return m;
            } catch (Throwable ignored) { }
        }
        return null;
    }

    /**
     * /dev/input/eventN of the REAL pen key device; null when absent. HyperOS's SELinux policy denies shell
     * /proc/bus/input/devices and sysfs names, so this asks `getevent -i`. Key Mapper's uinput copy carries the
     * same name, bus, vendor and product, but an empty id; the real device's id is the pen's Bluetooth address.
     */
    private static String findNode() {
        try {
            Process p = new ProcessBuilder("getevent", "-i").redirectErrorStream(true).start();
            String node = null, name = null, id = null, found = null;
            try (BufferedReader r = new BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("add device")) {
                        if (found == null && PEN.equals(name) && id != null && !id.isEmpty()) found = node;
                        node = line.substring(line.indexOf('/'));
                        name = null;
                        id = null;
                    } else if (line.trim().startsWith("name:")) {
                        name = unquote(line);
                    } else if (line.trim().startsWith("id:")) {
                        id = unquote(line);
                    }
                }
            }
            p.waitFor();
            if (found == null && PEN.equals(name) && id != null && !id.isEmpty()) found = node;
            return found;
        } catch (Exception e) {
            log("getevent -i failed: " + e);
            return null;
        }
    }

    private static String unquote(String line) {
        int a = line.indexOf('"'), b = line.lastIndexOf('"');
        return a >= 0 && b > a ? line.substring(a + 1, b) : "";
    }

    private static void exec(String cmd) {
        try {
            Runtime.getRuntime().exec(cmd).waitFor();
        } catch (Exception e) {
            log("exec failed: " + e);
        }
    }

    private static void log(String s) {
        System.out.println(System.currentTimeMillis() + " " + s);
        System.out.flush();
    }
}

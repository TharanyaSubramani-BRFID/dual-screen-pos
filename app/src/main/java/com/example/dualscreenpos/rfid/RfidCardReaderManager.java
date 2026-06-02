package com.example.dualscreenpos.rfid;

import android.content.Context;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/**
 * Manages the R9602 RFID Card Issuer connected via USB 2.0 / Serial (CDC ACM).
 * The reader streams EPC strings over serial at 9600 baud whenever a tag is in range.
 *
 * DEBUGGING: Filter Logcat by tag "BRFID_RFID" to see full USB enumeration details,
 * raw bytes received, and EPC parsing decisions. This is the first place to look
 * when hardware arrives and scanning doesn't work.
 */
public class RfidCardReaderManager {

    private static final String TAG = "BRFID_RFID";
    private static final int SCAN_TIMEOUT_MS = 3000;

    // CDC ACM USB constants
    private static final int USB_CLASS_CDC_COMM       = 0x02;
    private static final int USB_CLASS_CDC_DATA       = 0x0A;
    private static final int CDC_SET_LINE_CODING      = 0x20;
    private static final int CDC_SET_CONTROL_LINE_STATE = 0x22;
    // bmRequestType for CDC class commands: class | interface | host→device = 0x21
    private static final int CDC_REQUEST_TYPE         = 0x21;

    private static RfidCardReaderManager instance;

    private Context appContext;
    private volatile UsbDeviceConnection connection;
    private UsbInterface dataInterface;
    private UsbEndpoint bulkIn;
    private volatile boolean reading      = false;
    private volatile boolean expectingTag = false;

    private final MutableLiveData<ReaderState> stateLiveData = new MutableLiveData<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable scanTimeoutRunnable;

    private RfidCardReaderManager() {}

    public static void init(Context ctx) {
        if (instance == null) {
            instance = new RfidCardReaderManager();
            instance.appContext = ctx.getApplicationContext();
        }
    }

    public static RfidCardReaderManager getInstance() {
        return instance;
    }

    public MutableLiveData<ReaderState> getStateLiveData() {
        return stateLiveData;
    }

    // ── Connect ───────────────────────────────────────────────────────────────

    /** Find R9602 on USB and open serial connection. Safe to call from any thread. */
    public void connect() {
        new Thread(this::doConnect).start();
    }

    private void doConnect() {
        disconnect();

        StringBuilder diag = new StringBuilder();
        diag.append("=== R9602 USB Diagnostic ===\n");

        UsbManager manager = (UsbManager) appContext.getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> devices = manager.getDeviceList();

        // ── Step 1: Enumerate all USB devices ────────────────────────────────
        diag.append("\n[1] USB devices found: ").append(devices.size()).append("\n");
        Log.i(TAG, "[1] USB scan: " + devices.size() + " device(s) found");

        if (devices.isEmpty()) {
            String msg = "No USB devices detected. Connect R9602 USB cable.";
            diag.append("    → ").append(msg).append("\n");
            Log.w(TAG, msg);
            logAndPost(new ReaderState.ReaderError(msg, diag.toString()), diag);
            return;
        }

        for (UsbDevice d : devices.values()) {
            String deviceLine = String.format(
                    "  Device: %s  VID=0x%04X  PID=0x%04X  Class=0x%02X  Interfaces=%d",
                    d.getDeviceName(), d.getVendorId(), d.getProductId(),
                    d.getDeviceClass(), d.getInterfaceCount());
            diag.append(deviceLine).append("\n");
            Log.i(TAG, deviceLine);

            for (int i = 0; i < d.getInterfaceCount(); i++) {
                UsbInterface iface = d.getInterface(i);
                String ifaceLine = String.format(
                        "    Interface[%d]: class=0x%02X subclass=0x%02X protocol=0x%02X  endpoints=%d",
                        i, iface.getInterfaceClass(), iface.getInterfaceSubclass(),
                        iface.getInterfaceProtocol(), iface.getEndpointCount());
                diag.append(ifaceLine).append("\n");
                Log.i(TAG, ifaceLine);

                for (int j = 0; j < iface.getEndpointCount(); j++) {
                    UsbEndpoint ep = iface.getEndpoint(j);
                    String epType = ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK ? "BULK"
                            : ep.getType() == UsbConstants.USB_ENDPOINT_XFER_INT ? "INT"
                            : ep.getType() == UsbConstants.USB_ENDPOINT_XFER_ISOC ? "ISOC" : "CTRL";
                    String epDir = ep.getDirection() == UsbConstants.USB_DIR_IN ? "IN" : "OUT";
                    String epLine = String.format(
                            "      Endpoint[%d]: addr=0x%02X  type=%s  dir=%s  maxPkt=%d",
                            j, ep.getAddress(), epType, epDir, ep.getMaxPacketSize());
                    diag.append(epLine).append("\n");
                    Log.i(TAG, epLine);
                }
            }
        }

        // ── Step 2: Select target device ─────────────────────────────────────
        diag.append("\n[2] Device selection:\n");
        UsbDevice target = null;
        for (UsbDevice d : devices.values()) {
            if (isCdcDevice(d)) {
                target = d;
                diag.append("    → Selected (CDC class match): ").append(d.getDeviceName()).append("\n");
                Log.i(TAG, "[2] CDC device selected: " + d.getDeviceName()
                        + " VID=0x" + Integer.toHexString(d.getVendorId())
                        + " PID=0x" + Integer.toHexString(d.getProductId()));
                break;
            }
        }
        if (target == null) {
            target = devices.values().iterator().next();
            diag.append("    → No CDC device found. Fallback to first device: ")
                .append(target.getDeviceName()).append("\n");
            diag.append("    !! NOTE: R9602 is expected to have interface class 0x02 or 0x0A.\n");
            diag.append("    !! If this is R9602, it may use a vendor-specific class — contact ZSF.\n");
            Log.w(TAG, "[2] No CDC device — falling back to: " + target.getDeviceName());
        }

        // ── Step 3: Permission check ──────────────────────────────────────────
        diag.append("\n[3] USB permission check:\n");
        if (!manager.hasPermission(target)) {
            String msg = "USB permission denied. Unplug and replug R9602, then tap Allow.";
            diag.append("    → DENIED for ").append(target.getDeviceName()).append("\n");
            Log.e(TAG, "[3] " + msg);
            logAndPost(new ReaderState.ReaderError(msg, diag.toString()), diag);
            return;
        }
        diag.append("    → GRANTED for ").append(target.getDeviceName()).append("\n");
        Log.i(TAG, "[3] USB permission granted");

        // ── Step 4: Find CDC data interface → bulk-IN endpoint ───────────────
        diag.append("\n[4] Endpoint selection:\n");
        UsbInterface cdcIface = null;
        UsbEndpoint inEp     = null;

        // First pass: prefer CDC data interface (class 0x0A)
        search:
        for (int i = 0; i < target.getInterfaceCount(); i++) {
            UsbInterface iface = target.getInterface(i);
            if (iface.getInterfaceClass() == USB_CLASS_CDC_DATA) {
                for (int j = 0; j < iface.getEndpointCount(); j++) {
                    UsbEndpoint ep = iface.getEndpoint(j);
                    if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                            && ep.getDirection() == UsbConstants.USB_DIR_IN) {
                        cdcIface = iface; inEp = ep;
                        diag.append("    → Bulk-IN on CDC data interface[").append(i)
                            .append("] endpoint addr=0x")
                            .append(Integer.toHexString(ep.getAddress())).append("\n");
                        Log.i(TAG, "[4] Bulk-IN found on CDC data interface[" + i + "]");
                        break search;
                    }
                }
            }
        }

        // Fallback: any bulk-IN on any interface
        if (inEp == null) {
            diag.append("    → No CDC data interface. Trying fallback to any bulk-IN:\n");
            Log.w(TAG, "[4] No CDC data interface found, trying any bulk-IN");
            search:
            for (int i = 0; i < target.getInterfaceCount(); i++) {
                UsbInterface iface = target.getInterface(i);
                for (int j = 0; j < iface.getEndpointCount(); j++) {
                    UsbEndpoint ep = iface.getEndpoint(j);
                    if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                            && ep.getDirection() == UsbConstants.USB_DIR_IN) {
                        cdcIface = iface; inEp = ep;
                        diag.append("    → Fallback bulk-IN on interface[").append(i)
                            .append("] (class=0x")
                            .append(Integer.toHexString(iface.getInterfaceClass()))
                            .append(") endpoint addr=0x")
                            .append(Integer.toHexString(ep.getAddress())).append("\n");
                        Log.w(TAG, "[4] Fallback bulk-IN on interface[" + i + "]");
                        break search;
                    }
                }
            }
        }

        if (inEp == null) {
            String msg = "No bulk-IN endpoint found on device. R9602 may need a vendor driver.";
            diag.append("    → FAILED: ").append(msg).append("\n");
            diag.append("    !! All interfaces and endpoints listed above. Share this log with ZSF.\n");
            Log.e(TAG, "[4] " + msg);
            logAndPost(new ReaderState.ReaderError(msg, diag.toString()), diag);
            return;
        }

        // ── Step 5: Open USB connection ───────────────────────────────────────
        diag.append("\n[5] Opening USB connection:\n");
        UsbDeviceConnection conn = manager.openDevice(target);
        if (conn == null) {
            String msg = "Could not open R9602 USB connection.";
            diag.append("    → FAILED\n");
            Log.e(TAG, "[5] " + msg);
            logAndPost(new ReaderState.ReaderError(msg, diag.toString()), diag);
            return;
        }
        boolean claimed = conn.claimInterface(cdcIface, true);
        diag.append("    → Opened. Interface claimed=").append(claimed).append("\n");
        Log.i(TAG, "[5] Connection opened, interface claimed=" + claimed);

        // ── Step 6: Set serial parameters ─────────────────────────────────────
        diag.append("\n[6] Serial configuration (9600 baud, 8N1):\n");
        // 9600 baud = 0x00002580 little-endian
        byte[] lineCoding = {
                (byte) 0x80, 0x25, 0x00, 0x00,  // 9600 baud (uint32 LE)
                0x00,                              // bCharFormat: 1 stop bit
                0x00,                              // bParityType: none
                0x08                               // bDataBits: 8
        };
        int lcResult = conn.controlTransfer(
                CDC_REQUEST_TYPE, CDC_SET_LINE_CODING,
                0, 0, lineCoding, lineCoding.length, 2000);
        diag.append("    SET_LINE_CODING result=").append(lcResult)
            .append(lcResult >= 0 ? " (ok)" : " (failed — device may not support CDC ACM)").append("\n");
        Log.i(TAG, "[6] SET_LINE_CODING result=" + lcResult);

        // Assert DTR + RTS so reader knows host is ready
        int ctlResult = conn.controlTransfer(
                CDC_REQUEST_TYPE, CDC_SET_CONTROL_LINE_STATE,
                0x03, 0, null, 0, 2000);
        diag.append("    SET_CONTROL_LINE_STATE (DTR+RTS) result=").append(ctlResult)
            .append(ctlResult >= 0 ? " (ok)" : " (failed)").append("\n");
        Log.i(TAG, "[6] SET_CONTROL_LINE_STATE result=" + ctlResult);

        if (lcResult < 0) {
            diag.append("    !! controlTransfer failed. Possible causes:\n");
            diag.append("       - Device is not CDC ACM (different USB class)\n");
            diag.append("       - Device needs vendor-specific init instead\n");
            diag.append("       - Proceeding anyway — some devices ignore CDC setup\n");
            Log.w(TAG, "[6] controlTransfer failed — proceeding anyway");
        }

        connection  = conn;
        dataInterface = cdcIface;
        bulkIn      = inEp;

        diag.append("\n[7] Connected. Starting read loop.\n");
        Log.i(TAG, "=== R9602 connected: " + target.getDeviceName()
                + " VID=0x" + Integer.toHexString(target.getVendorId())
                + " PID=0x" + Integer.toHexString(target.getProductId()) + " ===");
        Log.i(TAG, "Full diagnostic:\n" + diag);

        postState(new ReaderState.Idle());
        startReadLoop();
    }

    // ── Continuous read loop ──────────────────────────────────────────────────

    private void startReadLoop() {
        reading = true;
        new Thread(() -> {
            byte[] buf = new byte[256];
            StringBuilder line = new StringBuilder();
            Log.i(TAG, "Read loop started — waiting for EPC data...");

            while (reading && connection != null) {
                int len = connection.bulkTransfer(bulkIn, buf, buf.length, 500);

                if (len > 0) {
                    // Log raw bytes as hex for debugging
                    StringBuilder hexDump = new StringBuilder();
                    for (int i = 0; i < len; i++) {
                        hexDump.append(String.format("%02X ", buf[i]));
                    }
                    Log.d(TAG, "Raw bytes [" + len + "]: " + hexDump.toString().trim());

                    String chunk = new String(buf, 0, len, StandardCharsets.US_ASCII);
                    for (char c : chunk.toCharArray()) {
                        if (c == '\n' || c == '\r') {
                            String raw = line.toString().trim();
                            line.setLength(0);
                            if (!raw.isEmpty()) {
                                Log.d(TAG, "Line received: \"" + raw + "\"");
                                handleEpcLine(raw);
                            }
                        } else {
                            line.append(c);
                        }
                    }
                }
                // len == 0: timeout, keep looping; len < 0: error
                if (len < 0 && reading) {
                    Log.w(TAG, "bulkTransfer returned " + len + " (disconnected?)");
                }
            }
            Log.i(TAG, "Read loop stopped");
        }).start();
    }

    private void handleEpcLine(String raw) {
        // Strip common prefixes: EPC:, EPC=, TID:, etc.
        String stripped = raw.replaceAll("(?i)^(epc|tid|id)[=:\\s]*", "");
        // Keep only hex characters
        String hex = stripped.replaceAll("[^0-9A-Fa-f]", "").toUpperCase();

        Log.d(TAG, "Parse: raw=\"" + raw + "\"  stripped=\"" + stripped
                + "\"  hex=\"" + hex + "\"  len=" + hex.length());

        // EPC-64 = 16 chars minimum, EPC-96 = 24 chars (standard for this project)
        if (hex.length() < 16) {
            Log.d(TAG, "Discarded (too short, expected ≥16 hex chars): \"" + raw + "\"");
            return;
        }

        Log.i(TAG, "EPC detected: " + hex + " (expecting=" + expectingTag + ")");

        if (expectingTag) {
            expectingTag = false;
            cancelScanTimeout();
            Log.i(TAG, "TagFound delivered to ViewModel: " + hex);
            postState(new ReaderState.TagFound(hex, 0));
        } else {
            Log.d(TAG, "EPC received but no scan active — ignored");
        }
    }

    // ── Scan ──────────────────────────────────────────────────────────────────

    /**
     * Arms the manager for one tag read. Posts Scanning immediately, then
     * TagFound on the next EPC from the reader or NoTagDetected after 3 s.
     */
    public void scanOnce() {
        if (connection == null) {
            String msg = "R9602 not connected. Check USB cable.";
            Log.e(TAG, "scanOnce: " + msg);
            postState(new ReaderState.ReaderError(msg));
            return;
        }
        cancelScanTimeout();
        expectingTag = true;
        Log.i(TAG, "scanOnce: armed, waiting up to " + SCAN_TIMEOUT_MS + "ms for EPC...");
        postState(new ReaderState.Scanning());

        scanTimeoutRunnable = () -> {
            if (expectingTag) {
                expectingTag = false;
                Log.i(TAG, "Scan timeout — no tag received in " + SCAN_TIMEOUT_MS + "ms");
                postState(new ReaderState.NoTagDetected());
            }
        };
        mainHandler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT_MS);
    }

    // ── Disconnect ────────────────────────────────────────────────────────────

    public void disconnect() {
        reading      = false;
        expectingTag = false;
        cancelScanTimeout();
        UsbDeviceConnection conn = connection;
        connection = null;
        if (conn != null) {
            try {
                if (dataInterface != null) conn.releaseInterface(dataInterface);
                conn.close();
                Log.i(TAG, "R9602 disconnected");
            } catch (Exception e) {
                Log.w(TAG, "Exception on disconnect: " + e.getMessage());
            }
        }
        dataInterface = null;
        bulkIn        = null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void cancelScanTimeout() {
        if (scanTimeoutRunnable != null) {
            mainHandler.removeCallbacks(scanTimeoutRunnable);
            scanTimeoutRunnable = null;
        }
    }

    private boolean isCdcDevice(UsbDevice d) {
        if (d.getDeviceClass() == USB_CLASS_CDC_COMM) return true;
        for (int i = 0; i < d.getInterfaceCount(); i++) {
            int cls = d.getInterface(i).getInterfaceClass();
            if (cls == USB_CLASS_CDC_COMM || cls == USB_CLASS_CDC_DATA) return true;
        }
        return false;
    }

    private void logAndPost(ReaderState.ReaderError error, StringBuilder diag) {
        Log.e(TAG, "Connection failed.\n" + diag.toString());
        postState(error);
    }

    private void postState(ReaderState s) {
        stateLiveData.postValue(s);
    }
}

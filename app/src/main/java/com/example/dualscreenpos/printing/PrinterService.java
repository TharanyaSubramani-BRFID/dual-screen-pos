package com.example.dualscreenpos.printing;

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

import com.example.dualscreenpos.data.model.ReceiptData;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

public class PrinterService {

    public interface PrintCallback {
        void onSuccess();
        void onError(String diagnostics);
    }

    private static final String TAG = "BRFID_PRINTER";

    // Serial ports to probe on Chinese Android POS hardware
    private static final String[] SERIAL_PORTS = {
            "/dev/ttyS0", "/dev/ttyS1", "/dev/ttyS2", "/dev/ttyS3",
            "/dev/ttyUSB0", "/dev/ttyUSB1"
    };

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public PrinterService(Context context) {
        this.context = context.getApplicationContext();
    }

    public void printReceipt(ReceiptData data, PrintCallback callback) {
        byte[] bytes = ReceiptFormatter.format(data);
        String textReceipt = ReceiptFormatter.formatText(data);

        // Always log the receipt text so it is always readable in Logcat
        Log.i(TAG, "=== RECEIPT ===\n" + textReceipt);

        new Thread(() -> {
            StringBuilder diag = new StringBuilder();

            // ── Attempt 1: USB Host ───────────────────────────────────────────
            diag.append("── USB Host ──\n");
            if (tryUsb(bytes, diag)) {
                deliver(callback::onSuccess);
                return;
            }

            // ── Attempt 2: Serial ports ───────────────────────────────────────
            diag.append("\n── Serial Ports ──\n");
            if (trySerial(bytes, diag)) {
                deliver(callback::onSuccess);
                return;
            }

            // ── All failed — build detailed error message ─────────────────────
            String fullError =
                    "Printer not found. Diagnostic log:\n\n"
                    + diag.toString()
                    + "\n\nReceipt content was saved to app log (filter by tag \"BRFID_PRINTER\")."
                    + "\n\nPossible fixes:\n"
                    + "• Ensure printer cable is connected\n"
                    + "• Grant USB permission if prompted\n"
                    + "• This device may need a vendor SDK — contact hardware supplier";

            Log.e(TAG, "Print failed.\n" + diag);
            deliver(() -> callback.onError(fullError));

        }).start();
    }

    // ── USB Host ──────────────────────────────────────────────────────────────

    private boolean tryUsb(byte[] data, StringBuilder diag) {
        try {
            UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            HashMap<String, UsbDevice> devices = manager.getDeviceList();

            diag.append("Devices found: ").append(devices.size()).append("\n");

            if (devices.isEmpty()) {
                diag.append("No USB devices detected.\n");
                return false;
            }

            for (Map.Entry<String, UsbDevice> entry : devices.entrySet()) {
                UsbDevice device = entry.getValue();
                diag.append("  Device: ").append(device.getDeviceName())
                    .append(" (VendorID=").append(device.getVendorId())
                    .append(" ProductID=").append(device.getProductId()).append(")\n");

                if (!manager.hasPermission(device)) {
                    diag.append("    → No USB permission (skipping)\n");
                    continue;
                }

                UsbEndpoint bulkOut = findBulkOut(device);
                if (bulkOut == null) {
                    diag.append("    → No bulk-OUT endpoint (not a printer)\n");
                    continue;
                }

                UsbInterface iface = getBulkInterface(device);
                UsbDeviceConnection conn = manager.openDevice(device);
                if (conn == null) {
                    diag.append("    → Failed to open connection\n");
                    continue;
                }

                conn.claimInterface(iface, true);
                int sent = conn.bulkTransfer(bulkOut, data, data.length, 5000);
                conn.releaseInterface(iface);
                conn.close();

                if (sent >= 0) {
                    diag.append("    → PRINTED OK via USB (").append(sent).append(" bytes)\n");
                    return true;
                } else {
                    diag.append("    → bulkTransfer returned ").append(sent).append(" (failed)\n");
                }
            }
        } catch (Exception e) {
            diag.append("USB exception: ").append(e.getMessage()).append("\n");
        }
        return false;
    }

    private UsbEndpoint findBulkOut(UsbDevice device) {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            for (int j = 0; j < iface.getEndpointCount(); j++) {
                UsbEndpoint ep = iface.getEndpoint(j);
                if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                        && ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                    return ep;
                }
            }
        }
        return null;
    }

    private UsbInterface getBulkInterface(UsbDevice device) {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            for (int j = 0; j < iface.getEndpointCount(); j++) {
                UsbEndpoint ep = iface.getEndpoint(j);
                if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                        && ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                    return iface;
                }
            }
        }
        return device.getInterface(0);
    }

    // ── Serial ports ──────────────────────────────────────────────────────────

    private boolean trySerial(byte[] data, StringBuilder diag) {
        for (String port : SERIAL_PORTS) {
            File f = new File(port);
            if (!f.exists()) {
                diag.append(port).append(": not found\n");
                continue;
            }
            try {
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(data);
                fos.flush();
                fos.close();
                diag.append(port).append(": PRINTED OK\n");
                return true;
            } catch (SecurityException e) {
                diag.append(port).append(": permission denied\n");
            } catch (Exception e) {
                diag.append(port).append(": ").append(e.getMessage()).append("\n");
            }
        }
        return false;
    }

    private void deliver(Runnable r) {
        mainHandler.post(r);
    }
}

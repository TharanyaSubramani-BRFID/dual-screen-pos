package com.example.dualscreenpos.ui.main;

import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.Display;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.dualscreenpos.R;
import com.example.dualscreenpos.presentation.CustomerPresentation;
import com.example.dualscreenpos.printing.PrinterService;
import com.example.dualscreenpos.rfid.ReaderState;
import com.example.dualscreenpos.rfid.RfidCardReaderManager;
import com.example.dualscreenpos.ui.settings.SettingsActivity;

public class MainActivity extends AppCompatActivity {

    private MainViewModel viewModel;
    private CustomerPresentation customerPresentation;
    private PowerManager.WakeLock wakeLock;
    private PrinterService printerService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "POS:WakeLock");
        wakeLock.acquire();

        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        initCustomerPresentation();
        printerService = new PrinterService(this);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        viewModel.getUiStateLiveData().observe(this, this::handleUiState);

        // Show RFID diagnostic dialog when connection fails — helps debug hardware issues
        RfidCardReaderManager.getInstance().getStateLiveData().observe(this, readerState -> {
            if (readerState instanceof ReaderState.ReaderError) {
                ReaderState.ReaderError err = (ReaderState.ReaderError) readerState;
                if (err.diagnostics != null) {
                    showRfidDiagnostic(err.message, err.diagnostics);
                }
            }
        });

        if (savedInstanceState == null) {
            showFragment(new IdleFragment());
        }
    }

    private void handleUiState(MainViewModel.UiState state) {
        if (state instanceof MainViewModel.UiState.StockCheck) {
            showFragment(new StockCheckFragment());

        } else if (state instanceof MainViewModel.UiState.Idle) {
            showFragment(new IdleFragment());
            if (customerPresentation != null) customerPresentation.showIdle();

        } else if (state instanceof MainViewModel.UiState.CartScanning) {
            showFragment(new ScanningFragment());
            if (customerPresentation != null) customerPresentation.showScanning();

        } else if (state instanceof MainViewModel.UiState.CartReady) {
            MainViewModel.UiState.CartReady s = (MainViewModel.UiState.CartReady) state;
            if (!(getSupportFragmentManager().findFragmentById(R.id.fragment_container) instanceof OperationsFragment)) {
                showFragment(new OperationsFragment());
            }
            if (customerPresentation != null) {
                double total = 0;
                for (com.example.dualscreenpos.data.model.ReturnRoute r : s.cart) {
                    if (r.skuDetail != null)
                        total += r.skuDetail.salePrice * (1 + r.skuDetail.gstPercent / 100.0);
                }
                String totalStr = total > 0 ? String.format("₹%.2f", total) : "";
                customerPresentation.showItemFound(s.cart.size() + " item(s)", totalStr);
            }

        } else if (state instanceof MainViewModel.UiState.Processing) {
            showFragment(new ProcessingFragment());
            if (customerPresentation != null) customerPresentation.showProcessing();

        } else if (state instanceof MainViewModel.UiState.Success) {
            MainViewModel.UiState.Success s = (MainViewModel.UiState.Success) state;
            showFragment(SuccessFragment.newInstance(s.itemName, s.type));
            if (customerPresentation != null) customerPresentation.showSuccess(s.itemName, s.type);

            // Auto-print receipt on checkout only
            if ("CHECKOUT".equals(s.type)) {
                com.example.dualscreenpos.data.model.ReceiptData receipt =
                        viewModel.getLastReceiptData();
                if (receipt != null) {
                    printerService.printReceipt(receipt, new PrinterService.PrintCallback() {
                        @Override public void onSuccess() {
                            android.widget.Toast.makeText(MainActivity.this,
                                    "Receipt printed", android.widget.Toast.LENGTH_SHORT).show();
                        }
                        @Override public void onError(String diagnostics) {
                            showPrintError(diagnostics);
                        }
                    });
                }
            }

        } else if (state instanceof MainViewModel.UiState.Error) {
            String msg = ((MainViewModel.UiState.Error) state).message;
            showFragment(ErrorFragment.newInstance(msg));
            if (customerPresentation != null) customerPresentation.showError(msg);

        } else if (state instanceof MainViewModel.UiState.BlockedItem) {
            MainViewModel.UiState.BlockedItem s = (MainViewModel.UiState.BlockedItem) state;
            new android.app.AlertDialog.Builder(this)
                    .setTitle(s.title)
                    .setMessage(s.message)
                    .setPositiveButton("OK", (d, w) -> viewModel.resumeCart())
                    .setCancelable(false)
                    .show();
        }
    }

    private void showFragment(Fragment f) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, f)
                .commit();
    }

    private void initCustomerPresentation() {
        DisplayManager dm = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        // Prefer displays flagged as presentation screens; fall back to any secondary display
        // (some dual-screen POS hardware exposes the second screen as a plain secondary display)
        Display[] displays = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        if (displays.length == 0) {
            Display[] all = dm.getDisplays();
            if (all.length > 1) displays = new Display[]{all[1]};
        }
        if (displays.length > 0) {
            customerPresentation = new CustomerPresentation(this, displays[0]);
            customerPresentation.show();
        }
    }

    private void showRfidDiagnostic(String message, String diagnostics) {
        String fullLog = "Filter Logcat by tag \"BRFID_RFID\" to see this log in real time.\n\n"
                + diagnostics
                + "\nWhat to do:\n"
                + "• Share this log with your ZSF hardware supplier\n"
                + "• Run 'adb shell lsusb -v' with R9602 connected\n"
                + "• Check ZSF docs for R9602 Android SDK or serial protocol";
        new android.app.AlertDialog.Builder(this)
                .setTitle("R9602 Connection Failed")
                .setMessage(message + "\n\nTechnical details below:")
                .setPositiveButton("OK", null)
                .setNeutralButton("Copy Log", (d, w) -> {
                    android.content.ClipboardManager cm =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("rfid_log", fullLog));
                    android.widget.Toast.makeText(this, "Copied to clipboard",
                            android.widget.Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showPrintError(String diagnostics) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Printer Not Connected")
                .setMessage(diagnostics)
                .setPositiveButton("OK", null)
                .setNeutralButton("Copy Log", (d, w) -> {
                    android.content.ClipboardManager cm =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("printer_log", diagnostics));
                    android.widget.Toast.makeText(this, "Copied to clipboard",
                            android.widget.Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    // R9602 plugged in while app is running → reconnect automatically
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
            RfidCardReaderManager.getInstance().connect();
        }
    }

    public MainViewModel getViewModel() {
        return viewModel;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (customerPresentation != null) customerPresentation.dismiss();
    }
}

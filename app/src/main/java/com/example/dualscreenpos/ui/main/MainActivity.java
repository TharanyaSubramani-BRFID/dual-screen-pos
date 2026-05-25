package com.example.dualscreenpos.ui.main;

import android.content.Intent;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.Display;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.dualscreenpos.R;
import com.example.dualscreenpos.data.repository.SettingsRepository;
import com.example.dualscreenpos.presentation.CustomerPresentation;
import com.example.dualscreenpos.rfid.ReaderState;
import com.example.dualscreenpos.ui.settings.SettingsActivity;

public class MainActivity extends AppCompatActivity {

    private MainViewModel viewModel;
    private CustomerPresentation customerPresentation;
    private PowerManager.WakeLock wakeLock;

    private View connectionDot;
    private TextView tvReaderIp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "POS:WakeLock");
        wakeLock.acquire();

        setContentView(R.layout.activity_main);

        connectionDot = findViewById(R.id.connection_dot);
        tvReaderIp = findViewById(R.id.tv_reader_ip);

        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        initCustomerPresentation();

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        viewModel.getReaderStateLiveData().observe(this, state -> updateStatusBar(state));

        // Also refresh the status bar when returning from Settings (mock mode may have changed).

        viewModel.getUiStateLiveData().observe(this, this::handleUiState);

        if (savedInstanceState == null) {
            showFragment(new IdleFragment());
        }
    }

    private void handleUiState(MainViewModel.UiState state) {
        if (state instanceof MainViewModel.UiState.Idle) {
            showFragment(new IdleFragment());
            if (customerPresentation != null) customerPresentation.showIdle();

        } else if (state instanceof MainViewModel.UiState.Scanning) {
            showFragment(new ScanningFragment());
            if (customerPresentation != null) customerPresentation.showScanning();

        } else if (state instanceof MainViewModel.UiState.ItemFound) {
            MainViewModel.UiState.ItemFound s = (MainViewModel.UiState.ItemFound) state;
            android.os.Bundle bundle = new android.os.Bundle();
            bundle.putString("route_json", new com.google.gson.Gson().toJson(s.route));
            showFragment(ItemFoundFragment.newInstance(bundle));
            if (customerPresentation != null)
                customerPresentation.showItemFound(s.getItemName(), s.getFormattedPrice());

        } else if (state instanceof MainViewModel.UiState.Processing) {
            showFragment(new ProcessingFragment());
            if (customerPresentation != null) customerPresentation.showProcessing();

        } else if (state instanceof MainViewModel.UiState.Success) {
            String name = ((MainViewModel.UiState.Success) state).itemName;
            showFragment(SuccessFragment.newInstance(name));
            if (customerPresentation != null) customerPresentation.showSuccess(name);

        } else if (state instanceof MainViewModel.UiState.Error) {
            String msg = ((MainViewModel.UiState.Error) state).message;
            showFragment(ErrorFragment.newInstance(msg));
            if (customerPresentation != null) customerPresentation.showError(msg);

        } else if (state instanceof MainViewModel.UiState.BlockedItem) {
            MainViewModel.UiState.BlockedItem s = (MainViewModel.UiState.BlockedItem) state;
            showFragment(new IdleFragment());
            new android.app.AlertDialog.Builder(this)
                    .setTitle(s.title)
                    .setMessage(s.message)
                    .setPositiveButton("OK", (d, w) -> {})
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
        Display[] displays = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
        if (displays.length > 0) {
            customerPresentation = new CustomerPresentation(this, displays[0]);
            customerPresentation.show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh after returning from Settings in case mock mode was toggled.
        updateStatusBar(viewModel != null ? viewModel.getReaderStateLiveData().getValue() : null);
    }

    private void updateStatusBar(ReaderState state) {
        if (SettingsRepository.getInstance().isMockMode()) {
            setDot(Color.parseColor("#FF9800"), "MOCK MODE");
            return;
        }
        if (state == null) {
            setDot(Color.GRAY, "Not connected");
        } else if (state instanceof ReaderState.ReaderError) {
            setDot(Color.parseColor("#F44336"), "Error");
        } else {
            setDot(Color.parseColor("#4CAF50"), SettingsRepository.getInstance().getReaderIp());
        }
    }

    private void setDot(int color, String label) {
        connectionDot.getBackground().setTint(color);
        tvReaderIp.setText(label);
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

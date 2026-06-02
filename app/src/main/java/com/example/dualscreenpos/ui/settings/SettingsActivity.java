package com.example.dualscreenpos.ui.settings;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.example.dualscreenpos.R;
import com.example.dualscreenpos.data.model.StorageBin;
import com.example.dualscreenpos.data.network.RetailApi;
import com.example.dualscreenpos.data.repository.BinRepository;
import com.example.dualscreenpos.data.repository.SettingsRepository;
import com.example.dualscreenpos.rfid.ReaderState;
import com.example.dualscreenpos.rfid.RfidCardReaderManager;

import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.settings_container, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        @Override
        @SuppressWarnings("unchecked")
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            getPreferenceManager().setSharedPreferencesName("pos_settings");
            Context ctx = requireContext();
            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(ctx);
            SettingsRepository settings = SettingsRepository.getInstance();

            EditTextPreference baseUrlPref = new EditTextPreference(ctx);
            baseUrlPref.setKey("base_url");
            baseUrlPref.setTitle("Backend URL");
            baseUrlPref.setSummaryProvider(pref -> settings.getBaseUrl());
            baseUrlPref.setOnPreferenceChangeListener((pref, newValue) -> {
                settings.setBaseUrl((String) newValue);
                return true;
            });

            Preference testConnectionPref = new Preference(ctx);
            testConnectionPref.setTitle("Test R9602 Connection");
            testConnectionPref.setSummary("Connect R9602 via USB, then tap to test");
            testConnectionPref.setOnPreferenceClickListener(pref -> {
                RfidCardReaderManager mgr = RfidCardReaderManager.getInstance();
                Observer<ReaderState>[] obs = new Observer[1];
                obs[0] = state -> {
                    if (state instanceof ReaderState.Idle) {
                        mgr.getStateLiveData().removeObserver(obs[0]);
                        Toast.makeText(ctx, "R9602 connected via USB", Toast.LENGTH_SHORT).show();
                    } else if (state instanceof ReaderState.ReaderError) {
                        mgr.getStateLiveData().removeObserver(obs[0]);
                        Toast.makeText(ctx, ((ReaderState.ReaderError) state).message,
                                Toast.LENGTH_LONG).show();
                    }
                };
                mgr.getStateLiveData().observeForever(obs[0]);
                mgr.connect();
                return true;
            });

            Preference testScanPref = new Preference(ctx);
            testScanPref.setTitle("Test Scan");
            testScanPref.setOnPreferenceClickListener(pref -> {
                RfidCardReaderManager mgr = RfidCardReaderManager.getInstance();
                Observer<ReaderState>[] obs = new Observer[1];
                obs[0] = state -> {
                    if (state instanceof ReaderState.TagFound) {
                        mgr.getStateLiveData().removeObserver(obs[0]);
                        Toast.makeText(ctx, "Tag: " + ((ReaderState.TagFound) state).epc, Toast.LENGTH_LONG).show();
                    } else if (state instanceof ReaderState.NoTagDetected) {
                        mgr.getStateLiveData().removeObserver(obs[0]);
                        Toast.makeText(ctx, "No tag detected", Toast.LENGTH_SHORT).show();
                    } else if (state instanceof ReaderState.ReaderError) {
                        mgr.getStateLiveData().removeObserver(obs[0]);
                        Toast.makeText(ctx, ((ReaderState.ReaderError) state).message, Toast.LENGTH_LONG).show();
                    }
                };
                mgr.getStateLiveData().observeForever(obs[0]);
                mgr.scanOnce();
                return true;
            });

            SwitchPreferenceCompat mockModePref = new SwitchPreferenceCompat(ctx);
            mockModePref.setKey("mock_mode");
            mockModePref.setTitle("Mock Mode");
            mockModePref.setSummary("Bypass RFID reader and pick EPC from a list");
            mockModePref.setChecked(settings.isMockMode());
            mockModePref.setOnPreferenceChangeListener((pref, newValue) -> {
                settings.setMockMode((Boolean) newValue);
                return true;
            });

            PreferenceCategory mockCategory = new PreferenceCategory(ctx);
            mockCategory.setTitle("Testing");
            screen.addPreference(mockCategory);
            mockCategory.addPreference(mockModePref);

            PreferenceCategory hwCategory = new PreferenceCategory(ctx);
            hwCategory.setTitle("Hardware");
            screen.addPreference(hwCategory);
            hwCategory.addPreference(testConnectionPref);
            hwCategory.addPreference(testScanPref);

            PreferenceCategory backendCategory = new PreferenceCategory(ctx);
            backendCategory.setTitle("Backend");
            screen.addPreference(backendCategory);
            backendCategory.addPreference(baseUrlPref);

            setPreferenceScreen(screen);
        }
    }
}

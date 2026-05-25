package com.example.dualscreenpos.ui.settings;

import android.content.Context;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

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

            EditTextPreference readerIpPref = new EditTextPreference(ctx);
            readerIpPref.setKey("reader_ip");
            readerIpPref.setTitle("Reader IP Address");
            readerIpPref.setSummaryProvider(pref -> settings.getReaderIp());
            readerIpPref.setOnPreferenceChangeListener((pref, newValue) -> {
                settings.setReaderIp((String) newValue);
                return true;
            });

            EditTextPreference baseUrlPref = new EditTextPreference(ctx);
            baseUrlPref.setKey("base_url");
            baseUrlPref.setTitle("Backend URL");
            baseUrlPref.setSummaryProvider(pref -> settings.getBaseUrl());
            baseUrlPref.setOnPreferenceChangeListener((pref, newValue) -> {
                settings.setBaseUrl((String) newValue);
                return true;
            });

            ListPreference antCountPref = new ListPreference(ctx);
            antCountPref.setKey("ant_count");
            antCountPref.setTitle("Antenna Count");
            antCountPref.setEntries(new String[]{"1", "2", "3", "4"});
            antCountPref.setEntryValues(new String[]{"1", "2", "3", "4"});
            antCountPref.setValue(String.valueOf(settings.getAntCount()));
            antCountPref.setOnPreferenceChangeListener((pref, newValue) -> {
                settings.setAntCount(Integer.parseInt((String) newValue));
                return true;
            });

            Preference testConnectionPref = new Preference(ctx);
            testConnectionPref.setTitle("Test Reader Connection");
            testConnectionPref.setOnPreferenceClickListener(pref -> {
                String ip = settings.getReaderIp();
                RfidCardReaderManager mgr = RfidCardReaderManager.getInstance();
                Observer<ReaderState>[] obs = new Observer[1];
                obs[0] = state -> {
                    if (state instanceof ReaderState.Idle) {
                        mgr.getStateLiveData().removeObserver(obs[0]);
                        Toast.makeText(ctx, "Connected to " + ip, Toast.LENGTH_SHORT).show();
                    } else if (state instanceof ReaderState.ReaderError) {
                        mgr.getStateLiveData().removeObserver(obs[0]);
                        Toast.makeText(ctx, ((ReaderState.ReaderError) state).message, Toast.LENGTH_SHORT).show();
                    }
                };
                mgr.getStateLiveData().observeForever(obs[0]);
                mgr.connect(ip);
                return true;
            });

            Preference reloadBinsPref = new Preference(ctx);
            reloadBinsPref.setTitle("Reload Bins");
            reloadBinsPref.setOnPreferenceClickListener(pref -> {
                BinRepository.getInstance().loadBins(new RetailApi.ApiCallback<List<StorageBin>>() {
                    @Override
                    public void onSuccess(List<StorageBin> result) {
                        Toast.makeText(ctx, "Loaded " + result.size() + " bins", Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onFailure(String err) {
                        Toast.makeText(ctx, "Failed to load bins: " + err, Toast.LENGTH_LONG).show();
                    }
                });
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

            screen.addPreference(readerIpPref);
            screen.addPreference(baseUrlPref);
            screen.addPreference(antCountPref);
            screen.addPreference(testConnectionPref);
            screen.addPreference(reloadBinsPref);
            screen.addPreference(testScanPref);
            setPreferenceScreen(screen);
        }
    }
}

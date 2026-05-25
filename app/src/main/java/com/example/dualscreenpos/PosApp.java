package com.example.dualscreenpos;

import android.app.Application;

import com.example.dualscreenpos.data.repository.BinRepository;
import com.example.dualscreenpos.data.repository.SettingsRepository;
import com.example.dualscreenpos.rfid.RfidCardReaderManager;

public class PosApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        SettingsRepository.init(this);
        RfidCardReaderManager.init(this);
        BinRepository.init(this);
        BinRepository.getInstance().loadBins(null);
    }
}

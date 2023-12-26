package io.agora.jkzy_demo;

import android.app.Application;

import io.agora.jkzy_demo.model.GlobalSettings;

public class App extends Application {
    private GlobalSettings globalSettings;

    public GlobalSettings getGlobalSettings() {
        if(globalSettings == null){
            globalSettings = new GlobalSettings();
        }
        return globalSettings;
    }
}

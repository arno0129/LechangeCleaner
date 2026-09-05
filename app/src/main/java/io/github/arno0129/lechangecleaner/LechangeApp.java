package io.github.arno0129.lechangecleaner;

import android.app.Application;
import java.util.concurrent.CopyOnWriteArraySet;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class LechangeApp extends Application implements XposedServiceHelper.OnServiceListener {
    private final CopyOnWriteArraySet<XposedServiceHelper.OnServiceListener> listeners = new CopyOnWriteArraySet<>();
    private XposedService service;

    @Override public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(this);
    }

    void addServiceListener(XposedServiceHelper.OnServiceListener listener) {
        listeners.add(listener);
        if (service != null) listener.onServiceBind(service);
    }

    void removeServiceListener(XposedServiceHelper.OnServiceListener listener) { listeners.remove(listener); }

    @Override public void onServiceBind(XposedService service) {
        this.service = service;
        for (XposedServiceHelper.OnServiceListener listener : listeners) listener.onServiceBind(service);
    }

    @Override public void onServiceDied(XposedService service) {
        this.service = null;
        for (XposedServiceHelper.OnServiceListener listener : listeners) listener.onServiceDied(service);
    }
}

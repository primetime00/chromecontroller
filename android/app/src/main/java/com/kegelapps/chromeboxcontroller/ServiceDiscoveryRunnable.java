package com.kegelapps.chromeboxcontroller;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.kegelapps.chromeboxcontroller.proto.DeviceInfoProto;
import com.kegelapps.chromeboxcontroller.proto.ServiceDiscoveryProto;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;

/**
 * Created by Ryan on 9/7/2015.
 */
public class ServiceDiscoveryRunnable implements Runnable {

    android.net.wifi.WifiManager.MulticastLock lock = null;
    private String type = "_workstation._tcp.local.";
    private String serviceName = "RemoteControl";
    private ServiceListener listener = null;
    private JmDNS jmdns = null;
    private Timer mTimeout;
    private boolean mActive;
    private ControllerService.OnServiceDiscovery discovery;
    private DeviceInfoProto.DeviceInfoList mDeviceCache;
    private Object mCacheLock = new Object();
    private boolean mComplete;

    private int timeout = 120000;
    private Context context;

    public ServiceDiscoveryRunnable(Context context, ControllerService.OnServiceDiscovery disc) {
        this.context = context;
        this.discovery = disc;
        mDeviceCache = DeviceInfoProto.DeviceInfoList.getDefaultInstance();
        mComplete = false;
    }

    @Override
    public void run() {

        android.net.wifi.WifiManager wifi = (android.net.wifi.WifiManager) context.getSystemService(android.content.Context.WIFI_SERVICE);

        lock = wifi.createMulticastLock(getClass().getSimpleName());
        lock.setReferenceCounted(false);
        mActive = true;
        mComplete = false;

        try {
            InetAddress addr = getLocalIpAddress();
            String hostname = addr.getHostName();
            lock.acquire();
            jmdns = JmDNS.create(addr, hostname);
            listener = new ServiceListener() {

                @Override
                public void serviceAdded(ServiceEvent event) {
                    if (event.getName().toLowerCase().contains(serviceName.toLowerCase()))
                        jmdns.requestServiceInfo(event.getType(), event.getName(), 3000);
                }

                @Override
                public void serviceRemoved(ServiceEvent ev) {
                    Log.d("ServiceDiscoveryRun", "Service removed: " + ev.getName());
                }

                @Override
                public void serviceResolved(ServiceEvent ev) {
                    DeviceInfoProto.DeviceInfo res = null;
                    Log.i("ServiceDiscoveryRun", "Service resolved: " + ev.getInfo().getQualifiedName() + " port:" + ev.getInfo().getPort());
                    Log.i("ServiceDiscoveryRun", "Service Type : " + ev.getInfo().getType());
                    if (ev.getInfo().getInet4Addresses().length == 0)
                        return;
                    Log.i("ServiceDiscoveryRun", "Service IP: " + ev.getInfo().getHostAddresses()[0]);
                    String service_ip = ev.getInfo().getHostAddresses()[0];
                    int service_port = ev.getInfo().getPort();
                    DeviceInfoProto.DeviceInfo.Builder builder = DeviceInfoProto.DeviceInfo.newBuilder();
                    builder.setIp(service_ip);
                    builder.setPort(service_port);

                    String name = ev.getInfo().getPropertyString("name");
                    String mac = ev.getInfo().getPropertyString("mac");
                    String location = ev.getInfo().getPropertyString("location");
                    String mode = ev.getInfo().getPropertyString("mode");

                    if (name != null)
                        builder.setName(name);
                    if (mac != null)
                        builder.setMac(mac);
                    else
                        Log.e("SerivceDiscoveryRun", "Cannot find a MAC on this service!  It won't work!");
                    if (location != null)
                        builder.setLocation(location);
                    if (mode != null)
                        builder.setMode(mode);

                    if (mac != null) {
                        res = builder.build();
                        processDevice(res);
                    }
                }
            };
            jmdns.addServiceListener(type, listener);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        mTimeout = new Timer();
        mTimeout.schedule(new TimerTask() {
            @Override
            public void run() {
                stop();
                mComplete = true;
            }
        }, timeout);
    }

    private void processDevice(DeviceInfoProto.DeviceInfo dev) {
        int dev_ip = UIHelpers.convertIp(dev.getIp());
        dev = dev.toBuilder().setUserCreated(false).setFound(true).build();
        for (DeviceInfoProto.DeviceInfo current : mDeviceCache.getDevicesList()) {
            int current_ip = UIHelpers.convertIp(current.getIp());
            if (dev_ip == current_ip) //we already have this device
                continue;
        }
        synchronized (mCacheLock) {
            mDeviceCache = mDeviceCache.toBuilder().addDevices(dev).build();
        }
        if (discovery != null)
            discovery.onDiscovery(dev);
    }

    public synchronized void stop() {
        //Unregister services
        if (jmdns == null) {
            mActive = false;
            return;
        }
        if (mTimeout != null)
            mTimeout.cancel();
        Log.i("ServiceDiscoveryRun", "Stopping mDNS!");
        if (jmdns != null) {
            if (listener != null) {
                jmdns.removeServiceListener(type, listener);
                listener = null;
            }
            jmdns.unregisterAllServices();
            try {
                jmdns.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            jmdns = null;
        }
        //Release the lock
        if (lock != null)
            lock.release();
        Log.i("ServiceDiscoveryRun", "mDNS Stopped!");
        mActive = false;
    }

    private InetAddress getLocalIpAddress() {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int ipAddress = wifiInfo.getIpAddress();
        InetAddress address = null;
        try {
            address = InetAddress.getByName(String.format(Locale.ENGLISH,
                    "%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff)));
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return address;
    }

    public boolean isActive() {
        return mActive;
    }

    public boolean isComplete() { return mComplete;}

    public void passCachedValues() {
        if (discovery != null) {
            DeviceInfoProto.DeviceInfoList cache;
            synchronized (mCacheLock) {
                cache = DeviceInfoProto.DeviceInfoList.newBuilder(mDeviceCache).build();
            }
            discovery.onCache(cache);
        }
    }
}

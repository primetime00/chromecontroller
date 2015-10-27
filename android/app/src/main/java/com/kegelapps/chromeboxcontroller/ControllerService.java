package com.kegelapps.chromeboxcontroller;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.kegelapps.chromeboxcontroller.proto.DeviceInfoProto;
import com.kegelapps.chromeboxcontroller.proto.MessageProto;
import com.kegelapps.chromeboxcontroller.proto.ScriptCommandProto;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Ryan on 9/7/2015.
 */
public class ControllerService extends Service {

    static final int SERVICE_PORT = 30015;

    static final int MESSAGE_BOUND                  = 0;
    static final int MESSAGE_START                  = 1;
    static final int MESSAGE_DISCOVERY              = 2;
    static final int MESSAGE_DISCOVERY_CACHE        = 3;
    static final int MESSAGE_CONNECTION_SUCCESS     = 4;
    static final int MESSAGE_CONNECTION_FAILED      = 5;
    static final int MESSAGE_CONNECTION_DISCONNECT  = 6;
    static final int MESSAGE_RECEIVED_MESSAGE       = 7;
    static final int MESSAGE_DISCONNECT_NETWORK     = 8;
    static final int MESSAGE_WAKE_SUCCESS           = 9;
    static final int MESSAGE_WAKE_FAILED            = 10;



    static final String MESSAGE_DATA_NAME_KEY           = "data";
    static final String MESSAGE_DISCONNECT_MESSAGE_KEY  = "reason";

    private final IBinder mBinder = new LocalBinder();
    private boolean mRunning = false;
    private Thread mServiceDiscoveryThread;
    private ServiceDiscoveryRunnable mDiscoveryRunnable;
    private NetworkRunnable mNetworkRunnable;
    private WakeRunnable mWakeRunnable;
    private Messenger mMessenger;
    private List<OnMessage> mMessageHandlerList;

    //storable info
    private DeviceInfoProto.DeviceInfo mCurrentDeviceInfo;
    private ScriptCommandProto.ScriptInfoList mScripts;
    private ScriptCommandProto.ScriptInfo mLastResult;
    private Object deviceLock = new Object();
    private Object scriptLock = new Object();
    private Object resultLock = new Object();


    public interface OnMessage {
        void onMessage(Message msg, MessageProto.Message data);

    }
    public interface OnServiceDiscovery {
        void onDiscovery(DeviceInfoProto.DeviceInfo data);
        void onCache(DeviceInfoProto.DeviceInfoList devices);
    }

    public interface OnWake {
        void onWakeSuccess();
        void onWakeFail();
    }

    public interface OnConnection {
        void onConnectionSuccess();
        void onConnectionFailed();
        void onDisconnected(String message);
        void onReceivedMessage(MessageProto.Message msg);
        void onDisconnectComplete();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mRunning || intent == null)
            return START_NOT_STICKY;
        mMessageHandlerList = new ArrayList<>();
        mMessenger = (Messenger)intent.getExtras().get("MESSENGER");
        Log.d("Service", "Starting service from thread " + Thread.currentThread());
        mRunning = true;
        createDiscoveryRunnable();
        createNetworkRunnable();
        createWakeRunnable();
        Message startMsg = Message.obtain();
        startMsg.what = MESSAGE_START;
        sendUIMessage(startMsg);
        return super.onStartCommand(intent, flags, startId);
    }

    private void createWakeRunnable() {
        mWakeRunnable = new WakeRunnable(getApplicationContext(), new OnWake() {
            @Override
            public void onWakeSuccess() {
                Log.d("Service", "Successfully sent wake packet");
                Message serviceMsg = Message.obtain();
                serviceMsg.what = MESSAGE_WAKE_SUCCESS;
                sendUIMessage(serviceMsg);
            }

            @Override
            public void onWakeFail() {
                Log.d("Service", "Successfully sent wake packet");
                Message serviceMsg = Message.obtain();
                serviceMsg.what = MESSAGE_WAKE_FAILED;
                sendUIMessage(serviceMsg);
            }
        });
    }

    private void createDiscoveryRunnable() {
        mDiscoveryRunnable = new ServiceDiscoveryRunnable(getApplicationContext(), new OnServiceDiscovery() {
            @Override
            public void onDiscovery(DeviceInfoProto.DeviceInfo data) {
                Log.d("Service", "I got a service device " + data.getIp());
                Message serviceMsg = Message.obtain();
                serviceMsg.what = MESSAGE_DISCOVERY;
                Bundle b = new Bundle();
                b.putByteArray(MESSAGE_DATA_NAME_KEY, data.toByteArray());
                serviceMsg.setData(b);
                sendUIMessage(serviceMsg);
            }

            @Override
            public void onCache(DeviceInfoProto.DeviceInfoList devices) {
                Log.d("Service", "I got a service cache of " + devices.getDevicesCount() + " devices");
                Message serviceMsg = Message.obtain();
                serviceMsg.what = MESSAGE_DISCOVERY_CACHE;
                Bundle b = new Bundle();
                b.putByteArray(MESSAGE_DATA_NAME_KEY, devices.toByteArray());
                serviceMsg.setData(b);
                sendUIMessage(serviceMsg);
            }
        });
    }

    private void createNetworkRunnable() {
        mNetworkRunnable = new NetworkRunnable(getApplicationContext(), new OnConnection() {
            @Override
            public void onConnectionFailed() {
                Message serviceMsg = Message.obtain();
                serviceMsg.what = MESSAGE_CONNECTION_FAILED;
                sendUIMessage(serviceMsg);
            }

            @Override
            public void onConnectionSuccess() {
                Message serviceMsg = Message.obtain();
                serviceMsg.what = MESSAGE_CONNECTION_SUCCESS;
                sendUIMessage(serviceMsg);
            }

            @Override
            public void onDisconnected(String message) {
                Message serviceMsg = Message.obtain();
                serviceMsg.what = MESSAGE_CONNECTION_DISCONNECT;
                Bundle b = new Bundle();
                b.putString(MESSAGE_DISCONNECT_MESSAGE_KEY, message);
                serviceMsg.setData(b);
                sendUIMessage(serviceMsg);
            }

            @Override
            public void onReceivedMessage(MessageProto.Message msg) {
                processMessage(msg);
            }

            @Override
            public void onDisconnectComplete() {
                Message serviceMsg = Message.obtain();
                serviceMsg.what = MESSAGE_DISCONNECT_NETWORK;
                sendUIMessage(serviceMsg);
            }
        });
    }

    private void processMessage(MessageProto.Message msg) {
        if (msg.hasDeviceInfo()) { //we have device info!
            storeDeviceInfo(msg.getDeviceInfo());
        }
        if (msg.hasCommandList()) { //we have commands
            storeDeviceCommands(msg.getCommandList());
            Log.d("RYAN", "Stored latest settings!");
        }
        if (msg.hasCommand() && msg.getCommand().hasReturnValue()) {//we have some return data to store
            storeLastCommandResult(msg.getCommand());
        }

        Message uiMessage = Message.obtain();
        uiMessage.what = MESSAGE_RECEIVED_MESSAGE;
        Bundle b = new Bundle();
        b.putByteArray(MESSAGE_DATA_NAME_KEY, msg.toByteArray());
        uiMessage.setData(b);
        Log.d("Service", "Sending message " + msg.toString() + " to UI");
        sendUIMessage(uiMessage);
    }

    public void storeDeviceCommands(ScriptCommandProto.ScriptInfoList cmdList) {
        synchronized (scriptLock) {
            mScripts = ScriptCommandProto.ScriptInfoList.newBuilder(cmdList).build();
        }
    }

    private void storeLastCommandResult(ScriptCommandProto.ScriptInfo result) {
        synchronized (resultLock) {
            if (result == null)
                mLastResult = null;
            else
                mLastResult = ScriptCommandProto.ScriptInfo.newBuilder(result).build();
        }
    }

    public void storeDeviceInfo(DeviceInfoProto.DeviceInfo deviceInfo) {
        synchronized (deviceLock) {
            mCurrentDeviceInfo = DeviceInfoProto.DeviceInfo.newBuilder(deviceInfo).build();
        }
    }

    public DeviceInfoProto.DeviceInfo getDeviceInfo() {
        if (mCurrentDeviceInfo == null)
            return null;
        synchronized (deviceLock) {
            return DeviceInfoProto.DeviceInfo.newBuilder(mCurrentDeviceInfo).build();
        }
    }

    public ScriptCommandProto.ScriptInfoList getDeviceCommands() {
        if (mScripts == null)
            return null;
        synchronized (scriptLock) {
            return ScriptCommandProto.ScriptInfoList.newBuilder(mScripts).build();
        }
    }

    public ScriptCommandProto.ScriptInfo getDeviceCommand(String name) {
        if (mScripts == null)
            return null;
        synchronized (scriptLock) {
            for (ScriptCommandProto.ScriptInfo current : mScripts.getScriptsList()) {
                if (current.getName().equals(name))
                    return current;
            }
            return null;
        }
    }


    public ScriptCommandProto.ScriptInfo getLastResult() {
        if (mLastResult == null)
            return null;
        synchronized (resultLock) {
            return ScriptCommandProto.ScriptInfo.newBuilder(mLastResult).build();
        }
    }



    private void sendUIMessage(Message msg) {
        try {
            mMessenger.send(msg);
        } catch (RemoteException e) {
            Log.e("Service", "Could not send a message " + msg.what + " to activity.");
            e.printStackTrace();
        }
    }

    public void sendNetworkMessage(MessageProto.Message msg) {
        if (mNetworkRunnable == null)
            return;
        if (msg.hasCommand()) //we are sending a script command, lets clear out the result value
            storeLastCommandResult(null);
        Message m = Message.obtain();
        m.what = NetworkRunnable.NETWORK_THREAD_SEND;
        Bundle b = new Bundle();
        b.putByteArray(NetworkRunnable.NETWORK_DATA_KEY, msg.toByteArray());
        m.setData(b);
        mNetworkRunnable.getHandler().sendMessage(m);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void startDiscovery() {
        if (mServiceDiscoveryThread != null) {//we've either run a scan or currently running one
            mDiscoveryRunnable.passCachedValues();
        }
        else {
            mServiceDiscoveryThread = new Thread(mDiscoveryRunnable);
            mServiceDiscoveryThread.start();
        }
    }

    public void stopDiscovery() {
        if (mDiscoveryRunnable == null)
            return;
        if (mServiceDiscoveryThread != null && mServiceDiscoveryThread.isAlive()) {
            try {
                mServiceDiscoveryThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        mServiceDiscoveryThread = new Thread(new Runnable() {
            @Override
            public void run() {
                mDiscoveryRunnable.stop();
            }
        });
        mServiceDiscoveryThread.start();

    }

    public boolean isDiscoveryActive() {
        if (mDiscoveryRunnable != null)
            return mDiscoveryRunnable.isActive();
        return false;
    }

    public boolean startNetwork(DeviceInfoProto.DeviceInfo dev) {
        if (mNetworkRunnable.isActive())
            return false;
        storeDeviceInfo(dev);
        mNetworkRunnable.setConnection(dev);
        new Thread(mNetworkRunnable).start();
        return true;
    }

    public void disconnectNetwork() {
        if (mNetworkRunnable != null && mNetworkRunnable.getHandler() != null)
            mNetworkRunnable.closeNetwork();
    }

    public void addMessageHandler(OnMessage handler) {
        if (handler != null && !mMessageHandlerList.contains(handler))
            mMessageHandlerList.add(handler);
    }

    public void removeMessageHandler(OnMessage handler) {
        if (handler != null)
            mMessageHandlerList.remove(handler);
    }

    public void clearMessageHandlers() {
        mMessageHandlerList.clear();
    }


    public List<OnMessage> getMessageHandlers() { return mMessageHandlerList; }

    public class LocalBinder extends Binder {
        ControllerService getService() {
            // Return this instance of LocalService so clients can call public methods
            return ControllerService.this;
        }
    }

    public void wakeDevice(DeviceInfoProto.DeviceInfo dev) {
        if (mWakeRunnable.isActive())
            return;
        mWakeRunnable.setDevice(dev);
        new Thread(mWakeRunnable).start();
    }

    public void requestConnectionState() {
        if (mNetworkRunnable ==null || (mNetworkRunnable != null && !mNetworkRunnable.isActive())) {
            Message serviceMsg = Message.obtain();
            serviceMsg.what = MESSAGE_DISCONNECT_NETWORK;
            Bundle b = new Bundle();
            b.putString(MESSAGE_DISCONNECT_MESSAGE_KEY, "");
            serviceMsg.setData(b);
            sendUIMessage(serviceMsg);
        }
    }


}


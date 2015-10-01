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

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Ryan on 9/7/2015.
 */
public class ControllerService extends Service {

    static final int MESSAGE_START                  = 1;
    static final int MESSAGE_DISCOVERY              = 2;
    static final int MESSAGE_CONNECTION_FAILED      = 3;
    static final int MESSAGE_CONNECTION_DISCONNECT  = 4;


    static final String MESSAGE_DATA_NAME_KEY           = "data";
    static final String MESSAGE_DISCONNECT_MESSAGE_KEY  = "reason";

    private final IBinder mBinder = new LocalBinder();
    private boolean mRunning = false;
    private Thread mRunningThread;
    private ServiceDiscoveryRunnable mDiscoveryRunnable;
    private NetworkRunnable mNetworkRunnable;
    private Messenger mMessenger;
    private List<OnMessage> mMessageHandlerList;

    public interface OnMessage {
        void onMessage(Message msg);

    }
    public interface OnServiceDiscovery {
        void onDiscovery(DeviceInfoProto.DeviceInfo data);
    }

    public interface OnConnection {
        void onConnectionFailed();
        void onDisconnected(String message);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mRunning || intent == null)
            return START_NOT_STICKY;
        mMessageHandlerList = new ArrayList<>();
        mMessenger = (Messenger)intent.getExtras().get("MESSENGER");
        Log.d("Service", "Starting service from thread " + Thread.currentThread());
        mRunning = true;
        mDiscoveryRunnable = new ServiceDiscoveryRunnable(getApplicationContext(), new OnServiceDiscovery() {
            @Override
            public void onDiscovery(DeviceInfoProto.DeviceInfo data) {
                Log.d("Service", "I got item from thread " + Thread.currentThread());
                Message serviceMsg = Message.obtain();
                serviceMsg.what = MESSAGE_DISCOVERY;
                Bundle b = new Bundle();
                b.putByteArray(MESSAGE_DATA_NAME_KEY, data.toByteArray());
                serviceMsg.setData(b);
                sendUIMessage(serviceMsg);
            }
        });
        createNetworkRunnable();
        Message startMsg = Message.obtain();
        startMsg.what = MESSAGE_START;
        sendUIMessage(startMsg);
        return super.onStartCommand(intent, flags, startId);
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
            public void onDisconnected(String message) {
                Message serviceMsg = Message.obtain();
                serviceMsg.what = MESSAGE_CONNECTION_DISCONNECT;
                Bundle b = new Bundle();
                b.putString(MESSAGE_DISCONNECT_MESSAGE_KEY, message);
                serviceMsg.setData(b);
                sendUIMessage(serviceMsg);
            }
        });
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
        mRunningThread = new Thread(mDiscoveryRunnable);
        mRunningThread.start();
    }
    public void stopDiscovery() {
        if (mDiscoveryRunnable == null)
            return;
        if (mRunningThread != null && mRunningThread.isAlive()) {
            try {
                mRunningThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        mRunningThread = new Thread(new Runnable() {
            @Override
            public void run() {
                mDiscoveryRunnable.stop();
            }
        });
        mRunningThread.start();

    }

    public boolean isDiscoveryActive() {
        if (mDiscoveryRunnable != null)
            return mDiscoveryRunnable.isActive();
        return false;
    }

    public void startNetwork(DeviceInfoProto.DeviceInfo dev) {
        mNetworkRunnable.setConnection(dev);
        new Thread(mNetworkRunnable).start();
    }

    public void addMessageHandler(OnMessage handler) {
        if (handler != null && !mMessageHandlerList.contains(handler))
            mMessageHandlerList.add(handler);
    }

    public void removeMessageHandler(OnMessage handler) {
        if (handler != null)
            mMessageHandlerList.remove(handler);
    }

    public List<OnMessage> getMessageHandlers() { return mMessageHandlerList; }

    public class LocalBinder extends Binder {
        ControllerService getService() {
            // Return this instance of LocalService so clients can call public methods
            return ControllerService.this;
        }
    }
}


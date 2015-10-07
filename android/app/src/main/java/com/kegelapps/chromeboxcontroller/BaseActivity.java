package com.kegelapps.chromeboxcontroller;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.google.protobuf.InvalidProtocolBufferException;
import com.kegelapps.chromeboxcontroller.proto.MessageProto;

import java.util.ArrayList;
import java.util.List;

public class BaseActivity extends AppCompatActivity {

    private ParentFragment mParentFragment;
    private ControllerService mControllerService;
    private boolean mServiceBound;
    private Messenger mServiceMessenger;
    private Storage mStorage;
    private List<Runnable> mBoundRunnableList;


    private CharSequence mTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setServiceBound(false);
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.container);
        if (f!=null)
            mParentFragment = (ParentFragment) f;
        else
            mParentFragment = new ParentFragment();
        mBoundRunnableList = new ArrayList<>();
        setContentView(R.layout.activity_base);

        mServiceMessenger = new Messenger(new ServiceMessageHandler());
        startNetworkService();

        mTitle = getTitle();

        if (f == null) {
            getSupportFragmentManager().beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                    .replace(R.id.container, mParentFragment)
                    .commit();
        }

        mStorage = new Storage(this);
        mStorage.loadUserDevices();


    }



    private void startNetworkService() {
        Intent i= new Intent(this, ControllerService.class);
        i.putExtra("MESSENGER", mServiceMessenger);
        getApplicationContext().startService(i);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_base, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isServiceBound()) {
            getApplicationContext().unbindService(mConnection);
            setServiceBound(false);
            mServiceMessenger = null;
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent i = new Intent(this, ControllerService.class);
        i.putExtra("MESSENGER", mServiceMessenger);
        getApplicationContext().bindService(i, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();

    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public ControllerService getService() {
        return mControllerService;
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            ControllerService.LocalBinder binder = (ControllerService.LocalBinder) service;
            mControllerService = binder.getService();
            setServiceBound(true);
            for (Runnable r : mBoundRunnableList) {
                r.run();
            }
            mBoundRunnableList.clear();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            setServiceBound(false);
            mControllerService = null;
        }
    };

    class ServiceMessageHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            MessageProto.Message data = null;
            switch (msg.what) {
                case ControllerService.MESSAGE_START:
                    mParentFragment.openDeviceList();
                    break;
                case ControllerService.MESSAGE_RECEIVED_MESSAGE:
                    try {
                        data = MessageProto.Message.parseFrom(msg.getData().getByteArray(ControllerService.MESSAGE_DATA_NAME_KEY));
                        Log.d("BaseActivity", "Received some data " + data.toString());
                    } catch (InvalidProtocolBufferException e) {
                        e.printStackTrace();
                        return;
                    }
            }
            if (mControllerService != null) {
                for (ControllerService.OnMessage handler : mControllerService.getMessageHandlers()) {
                    handler.onMessage(msg, data);
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        boolean res = true;
        if (mParentFragment != null)
            res = mParentFragment.onBackPressed();
        if (res == false)
            return;
        super.onBackPressed();
        if (isServiceBound()) {
            if (mControllerService != null) {
                if (mControllerService.isDiscoveryActive())
                    mControllerService.stopDiscovery();
                mControllerService.stopSelf();
            }
        }
    }

    public Storage getStorage() {
        return mStorage;
    }

    void runServiceItem(Runnable r) {
        if (isServiceBound())
            r.run();
        else
            mBoundRunnableList.add(r);
    }

    public boolean isServiceBound() {
        Log.d("Service", "Service bound read as " + mServiceBound);
        return mServiceBound;
    }

    public void setServiceBound(boolean bound) {
        Log.d("Service", "Service bound set to " + bound);
        this.mServiceBound = bound;
    }
}

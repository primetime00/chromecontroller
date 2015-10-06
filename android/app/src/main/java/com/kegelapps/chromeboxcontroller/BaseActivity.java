package com.kegelapps.chromeboxcontroller;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.support.v4.widget.DrawerLayout;

import com.google.protobuf.InvalidProtocolBufferException;
import com.kegelapps.chromeboxcontroller.proto.MessageProto;

import java.util.ArrayList;
import java.util.List;

public class BaseActivity extends AppCompatActivity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks {

    private ParentFragment mParentFragment;
    private ControllerService mControllerService;
    private boolean mServiceBound;
    private Messenger mServiceMessenger;
    private Storage mStorage;
    private List<Runnable> mBoundRunnableList;

    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private NavigationDrawerFragment mNavigationDrawerFragment;

    /**
     * Used to store the last screen title. For use in {@link #restoreActionBar()}.
     */
    private CharSequence mTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setServiceBound(false);
        mParentFragment = new ParentFragment();
        mBoundRunnableList = new ArrayList<>();
        setContentView(R.layout.activity_base);

        mServiceMessenger = new Messenger(new ServiceMessageHandler());
        startNetworkService();


        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        mTitle = getTitle();

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout));

        Fragment f = getSupportFragmentManager().findFragmentById(R.id.container);
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
    public void onNavigationDrawerItemSelected(int position) {
        // update the main content by replacing fragments
        FragmentManager fragmentManager = getSupportFragmentManager();
        //if (position == 0)
        //    mParentFragment.openDeviceList();
    }


    public void onSectionAttached(int number) {
        switch (number) {
            case 1:
                mTitle = "Device List";
                break;
            case 2:
                mTitle = getString(R.string.title_section2);
                break;
            case 3:
                mTitle = getString(R.string.title_section3);
                break;
        }
    }

    public void restoreActionBar() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setTitle(mTitle);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.menu_base, menu);
            restoreActionBar();
            return true;
        }
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

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
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.support.v4.widget.DrawerLayout;

public class BaseActivity extends AppCompatActivity
        implements NavigationDrawerFragment.NavigationDrawerCallbacks {

    private ParentFragment mParentFragment;
    private ControllerService mControllerService;
    private boolean mServiceBound = false;
    private Messenger mServiceMessenger;

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
        mParentFragment = new ParentFragment();
        setContentView(R.layout.activity_base);

        mNavigationDrawerFragment = (NavigationDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        mTitle = getTitle();

        // Set up the drawer.
        mNavigationDrawerFragment.setUp(
                R.id.navigation_drawer,
                (DrawerLayout) findViewById(R.id.drawer_layout));

        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(R.id.container, mParentFragment)
                .commit();

        mServiceMessenger = new Messenger(new ServiceMessageHandler());

        startNetworkService();

    }

    private void startNetworkService() {
        Intent i= new Intent(this, ControllerService.class);
        i.putExtra("MESSENGER", mServiceMessenger);
        startService(i);
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
    protected void onStop() {
        super.onStop();
        if (mServiceBound) {
            unbindService(mConnection);
            mServiceBound = false;
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent i = new Intent(this, ControllerService.class);
        i.putExtra("MESSENGER", mServiceMessenger);
        bindService(i, mConnection, Context.BIND_AUTO_CREATE);
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
            mServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mServiceBound = false;
        }
    };

    class ServiceMessageHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == ControllerService.MESSAGE_START) {
                mParentFragment.openDeviceList();
            }
            if (mControllerService != null && mServiceBound) {
                for (ControllerService.OnMessage handler : mControllerService.getMessageHandlers()) {
                    handler.onMessage(msg);
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (mServiceBound) {
            if (mControllerService != null) {
                if (mControllerService.isDiscoveryActive())
                    mControllerService.stopDiscovery();
                mControllerService.stopSelf();
            }
        }
    }
}

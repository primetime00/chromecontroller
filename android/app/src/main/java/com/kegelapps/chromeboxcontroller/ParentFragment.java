package com.kegelapps.chromeboxcontroller;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * Created by Ryan on 9/6/2015.
 */
public class ParentFragment extends Fragment implements FragmentOpener {

    private View mRootView;
    private DeviceListFragment mDeviceListFragment;
    private DeviceMenuFragment mDeviceMenuFragment;
    private DeviceSettingsFragment mDeviceSettingsFragment;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDeviceListFragment = new DeviceListFragment();
        mDeviceMenuFragment = new DeviceMenuFragment();
        mDeviceSettingsFragment = new DeviceSettingsFragment();

    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.fragment_parent, container, false);
        return mRootView;
    }

    @Override
    public void openFragment(Fragment frag, int where) {
        if (frag == null)
            return;
        FragmentManager fm = getChildFragmentManager();
        fm.beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                .replace(where, frag)
                .commit();
    }

    @Override
    public void openDeviceList() {
        openFragment(mDeviceListFragment, R.id.parent_content);
    }

    @Override
    public void openDeviceMenu() {
        openFragment(mDeviceMenuFragment, R.id.parent_content);
    }

    @Override
    public void openDeviceSettings() {
        openFragment(mDeviceSettingsFragment, R.id.parent_content);
    }
}

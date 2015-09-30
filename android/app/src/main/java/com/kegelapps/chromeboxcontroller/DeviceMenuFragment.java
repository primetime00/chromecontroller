package com.kegelapps.chromeboxcontroller;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTabHost;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TabHost;

/**
 * Created by Ryan on 9/6/2015.
 */
public class DeviceMenuFragment extends Fragment {
    private View mRootView;
    private FragmentTabHost mTabMenu;

    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.cb_device_menu, container, false);
        mTabMenu = (FragmentTabHost)mRootView.findViewById(android.R.id.tabhost);
        mTabMenu.setup(getActivity(), getChildFragmentManager(), android.R.id.tabcontent);
        mTabMenu.addTab(mTabMenu.newTabSpec("Info").setIndicator("Info"), DeviceInfoFragment.class, null);
        mTabMenu.addTab(mTabMenu.newTabSpec("Commands").setIndicator("Commands"), DeviceCommandsFragment.class, null);
        return mRootView;
    }

}

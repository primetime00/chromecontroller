package com.kegelapps.chromeboxcontroller;

import android.support.v4.app.Fragment;

/**
 * Created by Ryan on 9/6/2015.
 */
public interface FragmentOpener {
    void openFragment(Fragment frag, int where);
    void openDeviceList();
    void openDeviceMenu();
    void openDeviceSettings();
}

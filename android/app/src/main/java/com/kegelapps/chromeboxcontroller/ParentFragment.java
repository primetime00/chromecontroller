package com.kegelapps.chromeboxcontroller;

import android.app.FragmentTransaction;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;

/**
 * Created by Ryan on 9/6/2015.
 */
public class ParentFragment extends Fragment implements FragmentOpener {

    private View mRootView;
    private DeviceListFragment mDeviceListFragment;
    private DeviceMenuFragment mDeviceMenuFragment;
    private DeviceSettingsFragment mDeviceSettingsFragment;
    private CommandOutputFragment mCommandOutputFragment;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.fragment_parent, container, false);

        Fragment f = getChildFragmentManager().findFragmentById(R.id.parent_content);
        if (f != null) {
            if (f instanceof DeviceListFragment)
                mDeviceListFragment = (DeviceListFragment)f;
            else if (f instanceof DeviceMenuFragment)
                mDeviceMenuFragment = (DeviceMenuFragment)f;
            else if (f instanceof DeviceSettingsFragment)
                mDeviceSettingsFragment = (DeviceSettingsFragment)f;
            else if (f instanceof CommandOutputFragment)
                mCommandOutputFragment = (CommandOutputFragment)f;
        }
        if (mDeviceMenuFragment == null)
            mDeviceMenuFragment = new DeviceMenuFragment();
        if (mDeviceSettingsFragment == null)
            mDeviceSettingsFragment = new DeviceSettingsFragment();
        if (mDeviceListFragment == null)
            mDeviceListFragment = new DeviceListFragment();
        if (mCommandOutputFragment == null)
            mCommandOutputFragment = new CommandOutputFragment();

        return mRootView;
    }

    @Override
    public void openFragment(Fragment frag, int where, boolean push) {
        if (frag == null)
            return;
        FragmentManager fm = getChildFragmentManager();
        android.support.v4.app.FragmentTransaction tm = fm.beginTransaction().setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);
        if (!push) {
                    tm.replace(where, frag)
                    .commit();
        }
        else {
                    tm.addToBackStack(null).replace(where, frag)
                    .commit();

        }
    }

    @Override
    public void openDeviceList() {
        openFragment(mDeviceListFragment, R.id.parent_content, false);
    }

    @Override
    public void openDeviceMenu() {
        openFragment(mDeviceMenuFragment, R.id.parent_content, false);
    }

    @Override
    public void openDeviceSettings() {
        openFragment(mDeviceSettingsFragment, R.id.parent_content, false);
    }

    @Override
    public void openCommandResults() {
        openFragment(mCommandOutputFragment, R.id.parent_content, true);
    }

    @Override
    public void popBackStack() {
        getChildFragmentManager().popBackStack();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        UIHelpers.dismissDialog();

        //getActivity().getActionBar().
        try {
            Field childFragmentManager = Fragment.class.getDeclaredField("mChildFragmentManager");
            childFragmentManager.setAccessible(true);
            childFragmentManager.set(this, null);

        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        ControllerService service = UIHelpers.getService(this);
        if (service != null) {
            service.clearMessageHandlers();
        }

    }

    public boolean onBackPressed() {
        Fragment f = getChildFragmentManager().findFragmentById(R.id.parent_content);
        if (f == null)
            return true;
        if (f instanceof UIHelpers.OnFragmentCancelled) {
            return ((UIHelpers.OnFragmentCancelled) f).cancel();
        }
        return true;
    }
}

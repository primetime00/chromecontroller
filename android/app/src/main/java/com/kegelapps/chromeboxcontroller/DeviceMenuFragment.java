package com.kegelapps.chromeboxcontroller;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTabHost;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TabHost;

import com.kegelapps.chromeboxcontroller.proto.MessageProto;

/**
 * Created by Ryan on 9/6/2015.
 */
public class DeviceMenuFragment extends Fragment implements UIHelpers.OnFragmentCancelled {
    private View mRootView;
    private FragmentTabHost mTabMenu;
    private ControllerService.OnMessage mMessageHandler;
    private BaseActivity mActivity;

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
        mActivity = UIHelpers.getBaseActivity(this);
        createMessageHandler();
        return mRootView;
    }

    private void createMessageHandler() {
        mMessageHandler = new ControllerService.OnMessage() {
            @Override
            public void onMessage(Message msg, MessageProto.Message data) {
                switch (msg.what) {
                    case ControllerService.MESSAGE_DISCONNECT_NETWORK:
                        FragmentOpener op = UIHelpers.findFragmentOpener(DeviceMenuFragment.this);
                        if (op != null) {
                            op.openDeviceList();
                        }
                        break;
                    default:
                        break;
                }
            }
        };
        mActivity.runServiceItem(new Runnable() {
            @Override
            public void run() {
                ControllerService service = UIHelpers.getService(DeviceMenuFragment.this);
                if (service != null) {
                    service.addMessageHandler(mMessageHandler);
                }
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mActivity != null) {
            mActivity.runServiceItem(new Runnable() {
                @Override
                public void run() {
                    mActivity.getService().removeMessageHandler(mMessageHandler);
                }
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mActivity != null) {
            mActivity.runServiceItem(new Runnable() {
                @Override
                public void run() {
                    mActivity.getService().addMessageHandler(mMessageHandler);
                }
            });
        }
    }


    @Override
    public boolean cancel() {
        ControllerService service = UIHelpers.getService(this);
        if (service != null) {
            service.disconnectNetwork();
        } else {
            FragmentOpener op = UIHelpers.findFragmentOpener(this);
            if (op != null) {
                op.openDeviceList();
            }
        }
        return false;
    }
}

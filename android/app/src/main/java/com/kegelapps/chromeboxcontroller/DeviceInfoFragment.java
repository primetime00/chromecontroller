package com.kegelapps.chromeboxcontroller;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.kegelapps.chromeboxcontroller.proto.DeviceInfoProto;
import com.kegelapps.chromeboxcontroller.proto.InfoRequestProto;
import com.kegelapps.chromeboxcontroller.proto.InfoSetProto;
import com.kegelapps.chromeboxcontroller.proto.MessageProto;

/**
 * Created by Ryan on 9/7/2015.
 */
public class DeviceInfoFragment extends Fragment {
    private View mRootView;

    private TextView mDeviceName, mDeviceLocation, mDeviceMode, mDeviceIP, mDeviceMAC;
    private Button mNameChange, mLocationChange;

    private BaseActivity mActivity;

    private ControllerService.OnMessage mMessageHandler;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.cb_device_info, container, false);
        return mRootView;

    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mActivity = UIHelpers.getBaseActivity(this);
        mDeviceIP = (TextView) view.findViewById(R.id.ip);
        mDeviceMAC = (TextView) view.findViewById(R.id.mac);
        mDeviceMode = (TextView) view.findViewById(R.id.mode);
        mDeviceLocation = (TextView) view.findViewById(R.id.location);
        mDeviceName = (TextView) view.findViewById(R.id.name);

        mNameChange = (Button)view.findViewById(R.id.nameButton);
        mLocationChange = (Button)view.findViewById(R.id.locationButton);

        mNameChange.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeName();
            }
        });

        mLocationChange.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                changeLocation();
            }
        });

        createMessageHandler();

        mActivity.runServiceItem(new Runnable() {
            @Override
            public void run() {
                Log.d("DeviceInfoFragment", "Grabbing device info stored in service");
                processDeviceInfo(mActivity.getService().getDeviceInfo());

                InfoRequestProto.InfoRequest req = InfoRequestProto.InfoRequest.newBuilder().setId(0).build();
                MessageProto.Message msg = MessageProto.Message.newBuilder().setInfoRequest(req).build();
                Log.d("DeviceInfoFragment", "Requesting device info");
                mActivity.getService().sendNetworkMessage(msg);

            }
        });
    }

    private void createMessageHandler() {
        mMessageHandler = new ControllerService.OnMessage() {
            @Override
            public void onMessage(Message msg, MessageProto.Message data) {
                switch (msg.what) {
                    case ControllerService.MESSAGE_CONNECTION_DISCONNECT:
                        UIHelpers.showDisconnectDialog(getActivity(), msg.getData().getString(ControllerService.MESSAGE_DISCONNECT_MESSAGE_KEY, ""), new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                return;
                            }
                        });
                        break;
                    case ControllerService.MESSAGE_RECEIVED_MESSAGE:
                        if (data != null) {
                            processMessage(data);
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
                if (mActivity != null && mActivity.getService() != null) {
                    mActivity.getService().addMessageHandler(mMessageHandler);
                }
            }
        });
    }

    private void processMessage(MessageProto.Message data) {
        if (data.hasDeviceInfo()) {
            processDeviceInfo(data.getDeviceInfo());
        }
    }

    private void processDeviceInfo(DeviceInfoProto.DeviceInfo dev) {
        Log.d("DeviceInfoFragment", "I am processing some device info that I got");
        if (dev.hasName())
            mDeviceName.setText(dev.getName());
        if (dev.hasIp())
            mDeviceIP.setText(dev.getIp());
        if (dev.hasLocation())
            mDeviceLocation.setText(dev.getLocation());
        if (dev.hasMac())
            mDeviceMAC.setText(dev.getMac());
        if (dev.hasMode())
            mDeviceMode.setText(dev.getMode());
    }

    private void changeName() {
        UIHelpers.textEntry(getActivity(), "Name this device", new UIHelpers.OnDeviceTextEntry() {
            @Override
            public void onTextEntry(String name) {
                InfoSetProto.InfoSet info = InfoSetProto.InfoSet.newBuilder().setName(name).build();
                MessageProto.Message msg = MessageProto.Message.newBuilder().setInfoSet(info).build();
                if (mActivity != null && mActivity.getService() != null) {
                    mActivity.getService().sendNetworkMessage(msg);
                }
            }

            @Override
            public void onCancelled() {

            }
        });
    }

    private void changeLocation() {
        UIHelpers.textEntry(getActivity(), "Where is this device located?", new UIHelpers.OnDeviceTextEntry() {
            @Override
            public void onTextEntry(String name) {
                InfoSetProto.InfoSet info = InfoSetProto.InfoSet.newBuilder().setLocation(name).build();
                MessageProto.Message msg = MessageProto.Message.newBuilder().setInfoSet(info).build();
                if (mActivity != null && mActivity.getService() != null) {
                    mActivity.getService().sendNetworkMessage(msg);
                }
            }

            @Override
            public void onCancelled() {

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

}

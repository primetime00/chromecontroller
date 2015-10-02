package com.kegelapps.chromeboxcontroller;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import com.google.protobuf.InvalidProtocolBufferException;
import com.kegelapps.chromeboxcontroller.proto.DeviceInfoProto;
import com.kegelapps.chromeboxcontroller.proto.InfoSetProto;
import com.kegelapps.chromeboxcontroller.proto.MessageProto;

/**
 * Created by Ryan on 9/5/2015.
 */
public class DeviceListFragment extends Fragment implements UIHelpers.OnDeviceConnected{

    private View mRootView;
    private ListView mDeviceList;
    private DeviceListAdapter mAdapter;
    private ControllerService.OnMessage mMessageHandler;
    private BaseActivity mActivity;
    private DeviceInfoProto.DeviceInfo mDeviceInfo;


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.cb_device_list, container, false);
        return mRootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mDeviceList = (ListView)view.findViewById(R.id.device_list);
        mAdapter = new DeviceListAdapter(getActivity());

        createMessageHandler();
        DeviceInfoProto.DeviceInfo dev = DeviceInfoProto.DeviceInfo.newBuilder().
                setMode("Steam").setPort(30015).setLocation("Work").setIp("10.1.213.162").
                setId(1).setMac("08:00:27:7a:77:af").build();
        mAdapter.addDevice(dev);

        mDeviceList.setAdapter(mAdapter);

        mDeviceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mDeviceInfo = (DeviceInfoProto.DeviceInfo) mAdapter.getItem(position);
                connectToDevice(mDeviceInfo);
            }
        });
        mActivity = UIHelpers.getBaseActivity(this);
        if (mActivity != null) {
            mActivity.getService().addMessageHandler(mMessageHandler);
            mActivity.getService().startDiscovery();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mActivity != null && mActivity.getService() != null)
            mActivity.getService().removeMessageHandler(mMessageHandler);
    }

    private void createMessageHandler() {
        mMessageHandler = new ControllerService.OnMessage() {
            @Override
            public void onMessage(Message msg, MessageProto.Message data) {
                switch (msg.what)
                {
                    case ControllerService.MESSAGE_DISCOVERY:
                        byte [] serviceData = msg.getData().getByteArray(ControllerService.MESSAGE_DATA_NAME_KEY);
                        if (serviceData == null)
                            return;
                        try {
                            DeviceInfoProto.DeviceInfo disc = DeviceInfoProto.DeviceInfo.parseFrom(serviceData);
                            mAdapter.addDevice(disc);
                            mAdapter.notifyDataSetChanged();
                        } catch (InvalidProtocolBufferException e) {
                            Log.e("DeviceListFragment", "Could not parse ServiceDiscoveryProtocol");
                            e.printStackTrace();
                            return;
                        }
                        break;
                    case ControllerService.MESSAGE_CONNECTION_FAILED:
                        onDeviceConnectFailed();
                        break;
                    case ControllerService.MESSAGE_CONNECTION_SUCCESS:
                        onDeviceConnected();
                        break;
                    case ControllerService.MESSAGE_CONNECTION_DISCONNECT:
                        UIHelpers.showDisconnectDialog(getActivity(), msg.getData().getString(ControllerService.MESSAGE_DISCONNECT_MESSAGE_KEY, ""), new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface dialog) {
                                return;
                            }
                        });
                        break;
                    default:
                        break;
                }
            }
        };
    }

    private void connectToDevice(DeviceInfoProto.DeviceInfo deviceInfo) {
        if (mActivity == null || mActivity.getService() == null) {
            onDeviceConnectFailed();
        }
        mActivity.getService().startNetwork(deviceInfo);
    }

    private void openDeviceMenu() {
        FragmentOpener op = UIHelpers.findFragmentOpener(DeviceListFragment.this);
        if (op != null) {
            op.openDeviceMenu();
        }
    }


    private void stopDiscoveryService() {
        if (mActivity != null) {
            if (mActivity.getService().isDiscoveryActive())
                mActivity.getService().stopDiscovery();
        }
    }

    private void showConnectionFailedDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Failed to connect");
        builder.setMessage("Could not connect to the device.\nPlease ensure the device is turned on and try again");
        builder.setCancelable(true);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.show();
    }

    @Override
    public void onDeviceConnected() {
        stopDiscoveryService();
        if (!mDeviceInfo.hasName()) { //it doesn't have a name, allow a name.
            UIHelpers.textEntry(getActivity(), "Name this device", new UIHelpers.OnDeviceTextEntry() {
                @Override
                public void onTextEntry(String name) { //the device is named, lets push that name to the server
                    mDeviceInfo = mDeviceInfo.toBuilder().setName(name).build();
                    InfoSetProto.InfoSet info = InfoSetProto.InfoSet.newBuilder().setName(name).build();
                    MessageProto.Message msg = MessageProto.Message.newBuilder().setInfoSet(info).build();
                    if (mActivity != null && mActivity.getService() != null) {
                        mActivity.getService().sendNetworkMessage(msg);
                        openDeviceMenu();
                    }
                }

                @Override
                public void onCancelled() {
                    if (mActivity != null && mActivity.getService() != null) {
                        mActivity.getService().disconnectNetwork();
                    }
                }
            });
            return;
        }
        openDeviceMenu();
    }

    @Override
    public void onDeviceConnectFailed() {
        showConnectionFailedDialog();
    }
}

package com.kegelapps.chromeboxcontroller;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;

import com.google.protobuf.InvalidProtocolBufferException;
import com.kegelapps.chromeboxcontroller.proto.DeviceInfoProto;
import com.kegelapps.chromeboxcontroller.proto.InfoSetProto;
import com.kegelapps.chromeboxcontroller.proto.MessageProto;
import com.kegelapps.chromeboxcontroller.proto.PingProto;

/**
 * Created by Ryan on 9/5/2015.
 */
public class DeviceListFragment extends Fragment {

    private View mRootView;
    private ListView mDeviceList;
    private DeviceListAdapter mAdapter;
    private ControllerService.OnMessage mMessageHandler;
    private BaseActivity mActivity;
    private DeviceInfoProto.DeviceInfo mDeviceInfo;


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
                connectToDevice(mDeviceInfo, new UIHelpers.OnDeviceConnected() {
                    @Override
                    public void onDeviceConnected() { //we have connected to our device, lets see if it has a name!
                        stopDiscoveryService();
                        if (!mDeviceInfo.hasName()) { //it doesn't have a name, allow a name.
                            nameDevice(new UIHelpers.OnDeviceNamedListener() {
                                @Override
                                public void onDeviceNamed(String name) { //the device is named, lets push that name to the server
                                    mDeviceInfo = mDeviceInfo.toBuilder().setName(name).build();
                                    InfoSetProto.InfoSet info = InfoSetProto.InfoSet.newBuilder().setName(name).build();
                                    MessageProto.Message msg = MessageProto.Message.newBuilder().setInfoSet(info).build();
                                    if (mActivity != null && mActivity.getService() != null) {
                                        mActivity.getService().sendNetworkMessage(msg);
                                        openDeviceMenu();
                                    }
                                }
                            });
                            return;
                        }
                        openDeviceMenu();
                    }

                    @Override
                    public void onDeviceConnectFailed() { //we couldn't connect for whatever reason
                        showConnectionFailedDialog();
                    }
                });
            }
        });
        mActivity = UIHelpers.getBaseActivity(this);
        if (mActivity != null) {
            mActivity.getService().addMessageHandler(mMessageHandler);
            mActivity.getService().startDiscovery();
        }
    }

    private void openDeviceMenu() {
        FragmentOpener op = UIHelpers.findFragmentOpener(DeviceListFragment.this);
        if (op != null)
            op.openDeviceMenu();
    }

    private void connectToDevice(DeviceInfoProto.DeviceInfo deviceInfo, UIHelpers.OnDeviceConnected listener) {
        if (mActivity == null || mActivity.getService() == null) {
            listener.onDeviceConnectFailed();
        }
        mActivity.getService().startNetwork(deviceInfo);
    }

    private void stopDiscoveryService() {
        if (mActivity != null) {
            if (mActivity.getService().isDiscoveryActive())
                mActivity.getService().stopDiscovery();
        }
    }

    private void nameDevice(final UIHelpers.OnDeviceNamedListener listener) {
        assert (listener != null);
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Name this device");
        final EditText input = new EditText(getActivity());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String name = input.getText().toString();
                if (name.length() > 0)
                    listener.onDeviceNamed(name);
                dialog.dismiss();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();
    }

    private void createMessageHandler() {
        mMessageHandler = new ControllerService.OnMessage() {
            @Override
            public void onMessage(Message msg) {
                switch (msg.what)
                {
                    case ControllerService.MESSAGE_DISCOVERY:
                        byte [] data = msg.getData().getByteArray(ControllerService.MESSAGE_DATA_NAME_KEY);
                        if (data == null)
                            return;
                        try {
                            DeviceInfoProto.DeviceInfo disc = DeviceInfoProto.DeviceInfo.parseFrom(data);
                            mAdapter.addDevice(disc);
                            mAdapter.notifyDataSetChanged();
                        } catch (InvalidProtocolBufferException e) {
                            Log.e("DeviceListFragment", "Could not parse ServiceDiscoveryProtocol");
                            e.printStackTrace();
                            return;
                        }
                        break;
                    case ControllerService.MESSAGE_CONNECTION_FAILED:
                        showConnectionFailedDialog();
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

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.cb_device_list, container, false);
        return mRootView;
    }
}

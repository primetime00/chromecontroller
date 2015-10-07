package com.kegelapps.chromeboxcontroller;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
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
public class DeviceListFragment extends Fragment implements UIHelpers.OnDeviceConnected, UIHelpers.OnFragmentCancelled {

    private View mRootView;
    private ListView mDeviceList;
    private FloatingActionButton mAddButton;
    private DeviceListAdapter mAdapter;
    private ControllerService.OnMessage mMessageHandler;
    private BaseActivity mActivity;
    private DeviceInfoProto.DeviceInfo mDeviceInfo;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

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
        mAddButton = (FloatingActionButton) view.findViewById(R.id.add_button);
        mAdapter = new DeviceListAdapter(getActivity());

        mActivity = UIHelpers.getBaseActivity(this);

        //register a menu for our list
        registerForContextMenu(mDeviceList);

        createMessageHandler();

        loadUserDevices();

        mDeviceList.setAdapter(mAdapter);

        mDeviceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                connectToDevice(position);
            }
        });

        mAddButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDeviceSettings();
            }
        });

        if (mActivity != null) {
            mActivity.runServiceItem(new Runnable() {
                @Override
                public void run() {
                    mActivity.getService().addMessageHandler(mMessageHandler);
                    mActivity.getService().startDiscovery();
                }
            });
        }

    }

    private void loadUserDevices() {
        for (DeviceInfoProto.DeviceInfo dev : mActivity.getStorage().getUserDeviceList())
            mAdapter.addDevice(dev);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
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
                    mActivity.getService().startDiscovery(); //grab the cached items
                }
            });
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        if (v.getId() == R.id.device_list) {
            MenuInflater inflater = getActivity().getMenuInflater();
            inflater.inflate(R.menu.device_list_menu, menu);
            if (mDeviceList != null) {
                DeviceInfoProto.DeviceInfo dev = (DeviceInfoProto.DeviceInfo) mDeviceList.getAdapter().getItem(info.position);
                if (!dev.hasMac())
                    menu.removeItem(R.id.wake);
                if ( !(dev.hasUserCreated() && dev.getUserCreated() == true)) {
                    menu.removeItem(R.id.edit);
                    menu.removeItem(R.id.remove);
                }
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        DeviceInfoProto.DeviceInfo dev = (DeviceInfoProto.DeviceInfo)mAdapter.getItem(info.position);
        switch (item.getItemId()) {
            case R.id.remove:
                mActivity.getStorage().removeUserDevice(dev);
                mActivity.getStorage().saveUserDevices();
                mAdapter.removeItem(dev);
                mAdapter.notifyDataSetChanged();
                return true;
            case R.id.edit:
                mActivity.getService().storeDeviceInfo(dev);
                openDeviceSettings();
                return true;
            case R.id.wake:
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void createMessageHandler() {
        mMessageHandler = new ControllerService.OnMessage() {
            @Override
            public void onMessage(Message msg, MessageProto.Message data) {
                byte [] serviceData;
                switch (msg.what)
                {
                    case ControllerService.MESSAGE_DISCOVERY:
                        serviceData = msg.getData().getByteArray(ControllerService.MESSAGE_DATA_NAME_KEY);
                        if (serviceData == null)
                            return;
                        try {
                            DeviceInfoProto.DeviceInfo disc = DeviceInfoProto.DeviceInfo.parseFrom(serviceData);
                            addDeviceFromDiscovery(disc);
                        } catch (InvalidProtocolBufferException e) {
                            Log.e("DeviceListFragment", "Could not parse ServiceDiscoveryProtocol");
                            e.printStackTrace();
                            return;
                        }
                        break;
                    case ControllerService.MESSAGE_DISCOVERY_CACHE:
                        serviceData = msg.getData().getByteArray(ControllerService.MESSAGE_DATA_NAME_KEY);
                        if (serviceData == null)
                            return;
                        try {
                            DeviceInfoProto.DeviceInfoList devices = DeviceInfoProto.DeviceInfoList.parseFrom(serviceData);
                            addDeviceCache(devices);
                        } catch (InvalidProtocolBufferException e) {
                            Log.e("DeviceListFragment", "Could not parse ServiceDiscoveryProtocol Cache");
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

    private void addDeviceCache(DeviceInfoProto.DeviceInfoList devices) {
        for (DeviceInfoProto.DeviceInfo cached_item : devices.getDevicesList()) {
            DeviceInfoProto.DeviceInfo item = mAdapter.findDevice(cached_item);
            if (item != null) //this item is currently in the list, so replace it
                mAdapter.removeItem(item);
            mAdapter.addDevice(cached_item);
        }
        mAdapter.notifyDataSetChanged();
    }

    private void addDeviceFromDiscovery(DeviceInfoProto.DeviceInfo dev) {
        DeviceInfoProto.DeviceInfo adapterDev = mAdapter.findDevice(dev);
        if (adapterDev != null)
            mAdapter.removeItem(adapterDev);
        mAdapter.addDevice(dev);
        mAdapter.notifyDataSetChanged();
    }

    private void connectToDevice(int position) {
        if (mActivity == null || mActivity.getService() == null) {
            onDeviceConnectFailed();
            return;
        }
        DeviceInfoProto.DeviceInfo deviceInfo = (DeviceInfoProto.DeviceInfo) mAdapter.getItem(position);
        if (mActivity.getService().startNetwork(deviceInfo)) {
            mAdapter.setActiveDevice(position);
        }
        mAdapter.notifyDataSetChanged();
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

    private void openDeviceSettings() {
        FragmentOpener op = UIHelpers.findFragmentOpener(DeviceListFragment.this);
        if (op != null) {
            op.openDeviceSettings();
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
        mDeviceInfo = mActivity.getService().getDeviceInfo();
        mAdapter.setActiveDevice(-1);
        mAdapter.notifyDataSetChanged();
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
        mAdapter.setActiveDevice(-1);
        mAdapter.notifyDataSetChanged();
        showConnectionFailedDialog();
    }

    @Override
    public boolean cancel() {
        return true;
    }
}

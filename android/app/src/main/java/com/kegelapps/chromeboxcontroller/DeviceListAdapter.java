package com.kegelapps.chromeboxcontroller;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.kegelapps.chromeboxcontroller.proto.DeviceInfoProto;
import com.kegelapps.chromeboxcontroller.proto.ServiceDiscoveryProto;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Ryan on 9/5/2015.
 */
public class DeviceListAdapter extends BaseAdapter {
    private Context context;
    //private List<DeviceInfoProto.DeviceInfo> devices;
    private DeviceInfoProto.DeviceInfoList devices;
    private int mActiveDevice;

    public DeviceListAdapter(Context context) {
        this.context = context;
        devices = DeviceInfoProto.DeviceInfoList.getDefaultInstance();
        mActiveDevice = -1;
    }

    public void addDevice(DeviceInfoProto.DeviceInfo dev) {
        for (DeviceInfoProto.DeviceInfo d : devices.getDevicesList()) {
            if (d.getIp().equals(dev.getIp()))
                return;
        }
        devices = devices.toBuilder().addDevices(dev).build();
    }

    @Override
    public int getCount() {
        return devices.getDevicesCount();
    }

    @Override
    public Object getItem(int position) {
        return devices.getDevices(position);
    }

    @Override
    public long getItemId(int position) {
        return devices.getDevices(position).getId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            LayoutInflater inflater = (LayoutInflater)context.getSystemService (Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.cb_device_list_item, null);
        }
        DeviceInfoProto.DeviceInfo dev = (DeviceInfoProto.DeviceInfo) getItem(position);
        if (dev.hasName())
            ((TextView) v.findViewById(R.id.title_text)).setText(dev.getName());
        else
            ((TextView) v.findViewById(R.id.title_text)).setText(dev.getIp());
        if (dev.hasLocation())
            ((TextView)v.findViewById(R.id.description_text)).setText(dev.getLocation());
        else
            ((TextView)v.findViewById(R.id.description_text)).setText(dev.getIp());

        if (position == mActiveDevice) {
            v.findViewById(R.id.loading).setVisibility(View.VISIBLE);
        }
        else {
            v.findViewById(R.id.loading).setVisibility(View.GONE);
        }
        return v;
    }

    public void setActiveDevice(int position) {
        mActiveDevice = position;
    }

    public int getActiveDevice() { return mActiveDevice; }

    public void removeItem(DeviceInfoProto.DeviceInfo dev) {
        int pos = devices.getDevicesList().indexOf(dev);
        if (pos > -1)
            devices = devices.toBuilder().removeDevices(pos).build();
    }

    public DeviceInfoProto.DeviceInfo findDevice(DeviceInfoProto.DeviceInfo dev) {
        int dev_ip = UIHelpers.convertIp(dev.getIp());
        for (DeviceInfoProto.DeviceInfo current : devices.getDevicesList()) {
            if (UIHelpers.convertIp(current.getIp()) == dev_ip) {
                return current;
            }
        }
        return null;
    }
}

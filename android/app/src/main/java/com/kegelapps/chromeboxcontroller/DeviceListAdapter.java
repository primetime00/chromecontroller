package com.kegelapps.chromeboxcontroller;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.kegelapps.chromeboxcontroller.proto.DeviceInfoProto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Created by Ryan on 9/5/2015.
 */
public class DeviceListAdapter extends BaseAdapter {
    private Context context;
    private List<DeviceInfoProto.DeviceInfo> devices;
    private int mActiveDevice;

    public DeviceListAdapter(Context context) {
        this.context = context;
        mActiveDevice = -1;
        devices = new ArrayList<>();
    }

    public void addDevice(DeviceInfoProto.DeviceInfo dev) {
        int dev_ip = UIHelpers.convertIp(dev.getIp());
        for (DeviceInfoProto.DeviceInfo d : devices) {
            int current_ip = UIHelpers.convertIp(d.getIp());
            if (dev_ip == current_ip) {
                if (dev.getUserCreated() != d.getUserCreated())
                    continue;
                return;
            }
        }
        devices.add(dev);
        sortDevices();
    }

    private void sortDevices() {
        Collections.sort(devices, new Comparator<DeviceInfoProto.DeviceInfo>() {
            @Override
            public int compare(DeviceInfoProto.DeviceInfo lhs, DeviceInfoProto.DeviceInfo rhs) {
                if (lhs.getUserCreated() && rhs.getUserCreated()) { //both are user created, sort by name
                    return lhs.getName().compareTo(rhs.getName());
                } else if (lhs.getUserCreated() && !rhs.getUserCreated()) {
                    return 1;
                } else if (!lhs.getUserCreated() && rhs.getUserCreated()) {
                    return -1;
                } else {
                    return lhs.getName().compareTo(rhs.getName());
                }
            }
        });
    }

    @Override
    public int getCount() {
        return devices.size();
    }

    @Override
    public Object getItem(int position) {
        return devices.get(position);
    }

    @Override
    public long getItemId(int position) {
        return devices.get(position).getId();
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
        if (!dev.getUserCreated() && !dev.getFound()) {
            ((TextView) v.findViewById(R.id.title_text)).setEnabled(false);
            ((TextView) v.findViewById(R.id.description_text)).setEnabled(false);
        } else {
            ((TextView) v.findViewById(R.id.title_text)).setEnabled(true);
            ((TextView) v.findViewById(R.id.description_text)).setEnabled(true);
        }

        return v;
    }

    public void setActiveDevice(int position) {
        mActiveDevice = position;
    }

    public int getActiveDevice() { return mActiveDevice; }

    public void removeItem(DeviceInfoProto.DeviceInfo dev) {
        devices.remove(dev);
    }

    public DeviceInfoProto.DeviceInfo findDevice(DeviceInfoProto.DeviceInfo dev) {
        int dev_ip = UIHelpers.convertIp(dev.getIp());
        for (DeviceInfoProto.DeviceInfo current : devices) {
            if (UIHelpers.convertIp(current.getIp()) == dev_ip) {
                if (dev.getUserCreated() != current.getUserCreated())
                    continue;
                return current;
            }
        }
        return null;
    }
}

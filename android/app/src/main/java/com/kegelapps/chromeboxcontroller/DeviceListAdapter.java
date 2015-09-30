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
    private List<DeviceInfoProto.DeviceInfo> devices;

    public DeviceListAdapter(Context context) {
        this.context = context;
        devices = new ArrayList<DeviceInfoProto.DeviceInfo>();
    }

    public void addDevice(DeviceInfoProto.DeviceInfo dev) {
        for (DeviceInfoProto.DeviceInfo d : devices) {
            if (d.getIp().equals(dev.getIp()))
                return;
        }
        devices.add(dev);
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
        DeviceInfoProto.DeviceInfo dev = devices.get(position);
        if (dev.hasName())
            ((TextView) v.findViewById(R.id.title_text)).setText(dev.getName());
        else
            ((TextView) v.findViewById(R.id.title_text)).setText(dev.getIp());
        if (dev.hasLocation())
            ((TextView)v.findViewById(R.id.description_text)).setText(devices.get(position).getLocation());
        else
            ((TextView)v.findViewById(R.id.description_text)).setText(devices.get(position).getMac());
        return v;
    }
}

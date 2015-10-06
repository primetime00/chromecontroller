package com.kegelapps.chromeboxcontroller;

import android.content.Context;
import android.util.Log;

import com.kegelapps.chromeboxcontroller.proto.DeviceInfoProto;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Created by keg45397 on 10/2/2015.
 */
public class Storage {
    final private String saveFile = "user_devices";
    private Context context;
    private DeviceInfoProto.DeviceInfoList mDevices;

    public Storage(Context context) {
        this.context = context;
        mDevices = DeviceInfoProto.DeviceInfoList.getDefaultInstance();
    }

    void loadUserDevices() {
        File f = new File(context.getFilesDir().getAbsolutePath()+"/"+saveFile);
        if (!f.exists() || f.length() == 0)
            return;
        try {
            FileInputStream fin = context.openFileInput(saveFile);
            mDevices = DeviceInfoProto.DeviceInfoList.parseFrom(fin);
            fin.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("Storage", "Could not load user devices.");
        }
    }

    void addUserDevice(DeviceInfoProto.DeviceInfo dev) {
        dev = dev.toBuilder().setUserCreated(true).setPort(ControllerService.SERVICE_PORT).build();
        int dev_ip = UIHelpers.convertIp(dev.getIp());
        for (int i=0; i<mDevices.getDevicesCount(); ++i) {
            DeviceInfoProto.DeviceInfo current = mDevices.getDevices(i);
            if (UIHelpers.convertIp(current.getIp()) == dev_ip) { //we already have this, so update it
                mDevices = mDevices.toBuilder().setDevices(i, current.toBuilder().mergeFrom(dev).build()).build();
                return;
            }
        }
        mDevices = mDevices.toBuilder().addDevices(dev).build();
    }

    void saveUserDevices() {
        try {
            FileOutputStream fos = context.openFileOutput(saveFile, Context.MODE_PRIVATE);
            mDevices.writeTo(fos);
            fos.close();
        } catch (IOException e) {
            Log.e("Storage", "Could not save user devices.");
            e.printStackTrace();
        }
    }

    List<DeviceInfoProto.DeviceInfo> getUserDeviceList() {
        return mDevices.getDevicesList();
    }

    public void removeUserDevice(DeviceInfoProto.DeviceInfo dev) {
        int dev_ip = UIHelpers.convertIp(dev.getIp());
        for (int i=0; i<mDevices.getDevicesCount(); ++i) {
            DeviceInfoProto.DeviceInfo current = mDevices.getDevices(i);
            if (UIHelpers.convertIp(current.getIp()) == dev_ip && dev.getName().equals(current.getName())) {
                mDevices = mDevices.toBuilder().removeDevices(i).build();
                return;
            }
        }
    }

    public DeviceInfoProto.DeviceInfo findUserDevice(DeviceInfoProto.DeviceInfo dev) {
        int dev_ip = UIHelpers.convertIp(dev.getIp());
        for (int i=0; i<mDevices.getDevicesCount(); ++i) {
            DeviceInfoProto.DeviceInfo current = mDevices.getDevices(i);
            if (UIHelpers.convertIp(current.getIp()) == dev_ip) {
                return current;
            }
        }
        return null;
    }
}

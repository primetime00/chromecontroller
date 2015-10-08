package com.kegelapps.chromeboxcontroller;

import android.content.Context;
import android.util.Log;

import com.kegelapps.chromeboxcontroller.proto.DeviceInfoProto;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Created by keg45397 on 10/2/2015.
 */
public class Storage {
    final private String userFile = "user_devices";
    final private String seenFile = "seen_devices";
    private Context context;
    private DeviceInfoProto.DeviceInfoList mUserDevices, mSeenDevices;

    public Storage(Context context) {
        this.context = context;
        mUserDevices = DeviceInfoProto.DeviceInfoList.getDefaultInstance();
        mSeenDevices = DeviceInfoProto.DeviceInfoList.getDefaultInstance();
    }

    void loadUserDevices() {
        File f = new File(context.getFilesDir().getAbsolutePath()+"/"+ userFile);
        if (!f.exists() || f.length() == 0)
            return;
        try {
            FileInputStream fin = context.openFileInput(userFile);
            mUserDevices = DeviceInfoProto.DeviceInfoList.parseFrom(fin);
            fin.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("Storage", "Could not load user devices.");
        }
    }

    void addUserDevice(DeviceInfoProto.DeviceInfo dev) {
        dev = dev.toBuilder().setUserCreated(true).setPort(ControllerService.SERVICE_PORT).build();
        int dev_ip = UIHelpers.convertIp(dev.getIp());
        for (int i=0; i< mUserDevices.getDevicesCount(); ++i) {
            DeviceInfoProto.DeviceInfo current = mUserDevices.getDevices(i);
            if (UIHelpers.convertIp(current.getIp()) == dev_ip) { //we already have this, so update it
                mUserDevices = mUserDevices.toBuilder().setDevices(i, current.toBuilder().mergeFrom(dev).build()).build();
                return;
            }
        }
        mUserDevices = mUserDevices.toBuilder().addDevices(dev).build();
    }

    void saveUserDevices() {
        try {
            FileOutputStream fos = context.openFileOutput(userFile, Context.MODE_PRIVATE);
            mUserDevices.writeTo(fos);
            fos.close();
        } catch (IOException e) {
            Log.e("Storage", "Could not save user devices.");
            e.printStackTrace();
        }
    }

    List<DeviceInfoProto.DeviceInfo> getUserDeviceList() {
        return mUserDevices.getDevicesList();
    }

    public boolean removeUserDevice(DeviceInfoProto.DeviceInfo dev) {
        if (!dev.getUserCreated())
            return false;
        int dev_ip = UIHelpers.convertIp(dev.getIp());
        for (int i=0; i< mUserDevices.getDevicesCount(); ++i) {
            DeviceInfoProto.DeviceInfo current = mUserDevices.getDevices(i);
            if (UIHelpers.convertIp(current.getIp()) == dev_ip && dev.getName().equals(current.getName())) {
                mUserDevices = mUserDevices.toBuilder().removeDevices(i).build();
                return true;
            }
        }
        return false;
    }

    public DeviceInfoProto.DeviceInfo findUserDevice(DeviceInfoProto.DeviceInfo dev) {
        int dev_ip = UIHelpers.convertIp(dev.getIp());
        for (int i=0; i< mUserDevices.getDevicesCount(); ++i) {
            DeviceInfoProto.DeviceInfo current = mUserDevices.getDevices(i);
            if (UIHelpers.convertIp(current.getIp()) == dev_ip) {
                return current;
            }
        }
        return null;
    }

    public void addSeenDevice(DeviceInfoProto.DeviceInfo dev) {
        dev = dev.toBuilder().setUserCreated(false).setPort(ControllerService.SERVICE_PORT).build();
        int dev_ip = UIHelpers.convertIp(dev.getIp());
        for (int i=0; i<mSeenDevices.getDevicesCount(); ++i) {
            DeviceInfoProto.DeviceInfo current = mSeenDevices.getDevices(i);
            if (UIHelpers.convertIp(current.getIp()) == dev_ip) { //we already have this, so update it
                mSeenDevices = mSeenDevices.toBuilder().setDevices(i, current.toBuilder().mergeFrom(dev).build()).build();
                return;
            }
        }
        mSeenDevices = mSeenDevices.toBuilder().addDevices(dev).build();
    }

    public void saveSeenDevices() {
        try {
            FileOutputStream fos = context.openFileOutput(seenFile, Context.MODE_PRIVATE);
            mSeenDevices.writeTo(fos);
            fos.close();
        } catch (IOException e) {
            Log.e("Storage", "Could not save user devices.");
            e.printStackTrace();
        }
    }

    void loadSeenDevices() {
        File f = new File(context.getFilesDir().getAbsolutePath()+"/"+ userFile);
        if (!f.exists() || f.length() == 0)
            return;
        try {
            FileInputStream fin = context.openFileInput(seenFile);
            mSeenDevices = DeviceInfoProto.DeviceInfoList.parseFrom(fin);
            for (int i=0; i<mSeenDevices.getDevicesCount(); ++i) {
                DeviceInfoProto.DeviceInfo dev = mSeenDevices.getDevices(i);
                mSeenDevices = mSeenDevices.toBuilder().setDevices(i, dev.toBuilder().setFound(false).build()).build();
            }
            fin.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("Storage", "Could not load user devices.");
        }
    }

    public boolean removeSeenDevice(DeviceInfoProto.DeviceInfo dev) {
        if (dev.getUserCreated())
            return false;
        int dev_ip = UIHelpers.convertIp(dev.getIp());
        for (int i=0; i< mSeenDevices.getDevicesCount(); ++i) {
            DeviceInfoProto.DeviceInfo current = mSeenDevices.getDevices(i);
            if (UIHelpers.convertIp(current.getIp()) == dev_ip && dev.getName().equals(current.getName())) {
                mSeenDevices = mSeenDevices.toBuilder().removeDevices(i).build();
                return true;
            }
        }
        return false;
    }


    List<DeviceInfoProto.DeviceInfo> getSeenDeviceList() {
        return mSeenDevices.getDevicesList();
    }

}

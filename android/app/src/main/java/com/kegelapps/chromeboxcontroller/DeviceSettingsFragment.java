package com.kegelapps.chromeboxcontroller;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.kegelapps.chromeboxcontroller.proto.DeviceInfoProto;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by keg45397 on 10/2/2015.
 */
public class DeviceSettingsFragment extends Fragment {

    private View mRootView;
    private View mDeviceNameButton, mDeviceLocationButton, mDeviceIPButton, mDeviceMACButton;
    private TextView mDeviceName, mDeviceLocation, mDeviceIP, mDeviceMAC;
    private Button mSaveButton, mCancelButton;
    private BaseActivity mActivity;
    private DeviceInfoProto.DeviceInfo mDeviceInfo;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.cb_device_settings, container, false);
        return mRootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mActivity = UIHelpers.getBaseActivity(this);
        mDeviceInfo = mActivity.getService().getDeviceInfo();
        if (mDeviceInfo == null)
            mDeviceInfo = DeviceInfoProto.DeviceInfo.getDefaultInstance();
        mDeviceNameButton = view.findViewById(R.id.device_name);
        mDeviceLocationButton = view.findViewById(R.id.device_location);
        mDeviceIPButton = view.findViewById(R.id.device_ip);
        mDeviceMACButton = view.findViewById(R.id.device_mac);

        mDeviceName = (TextView) view.findViewById(R.id.name);
        mDeviceLocation = (TextView) view.findViewById(R.id.location);
        mDeviceIP = (TextView) view.findViewById(R.id.ip);
        mDeviceMAC = (TextView) view.findViewById(R.id.mac);

        mSaveButton = (Button) view.findViewById(R.id.save);
        mCancelButton = (Button) view.findViewById(R.id.cancel);


        setupSettings();
        setupButtons();
        updateView();

    }

    private void setupButtons() {
        mSaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mDeviceInfo.hasIp() && mDeviceInfo.hasName()) { //this is good enough for me
                    mActivity.getStorage().addUserDevice(mDeviceInfo);
                    mActivity.getStorage().saveUserDevices();
                    openDeviceList();
                }
                else
                    showError("Cannot Save", "The device requires both a name and and IP address to save.");
            }
        });

        mCancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openDeviceList();
            }
        });
    }

    private void setupSettings() {
        mDeviceNameButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UIHelpers.textEntry(getActivity(), "Enter device name", new UIHelpers.OnDeviceTextEntry() {
                    @Override
                    public void onTextEntry(String name) {
                        mDeviceInfo = mDeviceInfo.toBuilder().setName(name).build();
                        updateView();
                    }

                    @Override
                    public void onCancelled() {

                    }
                });
            }
        });

        mDeviceLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UIHelpers.textEntry(getActivity(), "Enter device location", new UIHelpers.OnDeviceTextEntry() {
                    @Override
                    public void onTextEntry(String name) {
                        mDeviceInfo = mDeviceInfo.toBuilder().setLocation(name).build();
                        updateView();
                    }

                    @Override
                    public void onCancelled() {

                    }
                });
            }
        });

        mDeviceIPButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UIHelpers.textEntry(getActivity(), "Enter device IP address", new UIHelpers.OnDeviceTextEntry() {
                    @Override
                    public void onTextEntry(String name) {
                        Matcher matcher = Patterns.IP_ADDRESS.matcher(name);
                        if (matcher.matches()) { //this is an IP!
                            mDeviceInfo = mDeviceInfo.toBuilder().setIp(name).build();
                            updateView();
                            return;
                        }
                        showError("Invalid MAC", "The entry is not a valid IP address.\nExample: 192.168.1.1");
                    }

                    @Override
                    public void onCancelled() {

                    }
                });
            }
        });

        mDeviceMACButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UIHelpers.textEntry(getActivity(), "Enter device MAC", new UIHelpers.OnDeviceTextEntry() {
                    @Override
                    public void onTextEntry(String name) {
                        Pattern pat = Pattern.compile("([\\da-fA-F]{2}(?:\\:|-|$)){6}");
                        Matcher matcher = pat.matcher(name);
                        if (matcher.matches()) { //this is not an IP!
                            name = name.replace("-", ":");
                            mDeviceInfo = mDeviceInfo.toBuilder().setMac(name).build();
                            updateView();
                            return;
                        }
                        showError("Invalid IP", "The entry is not a valid MAC address.\nExample: 00-AA-BB-12-CD-43");
                    }

                    @Override
                    public void onCancelled() {

                    }
                });
            }
        });


    }

    private void updateView() {
        if (mDeviceInfo.hasName()) {
            mDeviceName.setVisibility(View.VISIBLE);
            mDeviceName.setText(mDeviceInfo.getName());
        } else {
            mDeviceName.setVisibility(View.GONE);
        }
        if (mDeviceInfo.hasLocation()) {
            mDeviceLocation.setVisibility(View.VISIBLE);
            mDeviceLocation.setText(mDeviceInfo.getLocation());
        } else {
            mDeviceLocation.setVisibility(View.GONE);
        }
        if (mDeviceInfo.hasIp()) {
            mDeviceIP.setVisibility(View.VISIBLE);
            mDeviceIP.setText(mDeviceInfo.getIp());
        } else {
            mDeviceIP.setVisibility(View.GONE);
        }
        if (mDeviceInfo.hasMac()) {
            mDeviceMAC.setVisibility(View.VISIBLE);
            mDeviceMAC.setText(mDeviceInfo.getMac());
        } else {
            mDeviceMAC.setVisibility(View.GONE);
        }
    }

    private void showError(final String title, final String message) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setTitle(title);
                builder.setMessage(message);
                builder.setCancelable(true);
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                builder.show();
            }
        });
    }

    void openDeviceList() {
        FragmentOpener op = UIHelpers.findFragmentOpener(this);
        if (op != null) {
            op.openDeviceList();
        }
    }

}
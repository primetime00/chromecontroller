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

import com.kegelapps.chromeboxcontroller.proto.MessageProto;
import com.kegelapps.chromeboxcontroller.proto.ScriptCommandProto;

/**
 * Created by Ryan on 9/7/2015.
 */
public class DeviceCommandsFragment extends Fragment {

    private View mRootView;
    private BaseActivity mActivity;
    private ListView mCommandList;
    private CommandListAdapter mAdapter;

    private ControllerService.OnMessage mMessageHandler;


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.cb_device_commands, container, false);
        return mRootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mCommandList = (ListView)view.findViewById(R.id.commandList);
        mAdapter = new CommandListAdapter(getActivity());
        mCommandList.setAdapter(mAdapter);

        mActivity = UIHelpers.getBaseActivity(this);
        createMessageHandler();

        mCommandList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final ScriptCommandProto.ScriptInfo info = (ScriptCommandProto.ScriptInfo) mAdapter.getItem(position);
                runCommand(info);
            }
        });

        mActivity.runServiceItem(new Runnable() {
            @Override
            public void run() {
                Log.d("DeviceInfoFragment", "Grabbing device command list");
                processDeviceCommands(mActivity.getService().getDeviceCommands());
            }
        });


    }

    private void runCommand(final ScriptCommandProto.ScriptInfo info) {
        if (info.hasChoice() && info.getChoice().getOptionCount() > 0) {
            UIHelpers.choiceEntry(getActivity(), info.getChoice().getTitle(), info.getChoice().getOptionList(), new UIHelpers.OnChoiceSelected() {
                @Override
                public void onChoiceSelected(String choice) {
                    ScriptCommandProto.ScriptInfo item = info.toBuilder().addParams(choice).build();
                    sendMessage(item);
                }
            });
        }
        else
            sendMessage(info);
    }

    private void sendMessage(ScriptCommandProto.ScriptInfo info) {
        MessageProto.Message msg = MessageProto.Message.newBuilder().setCommand(info).build();
        if (mActivity != null && mActivity.getService() != null) {
            mActivity.getService().sendNetworkMessage(msg);
        }
    }

    private void processDeviceCommands(ScriptCommandProto.ScriptInfoList commands) {
        if (commands != null) {
            mAdapter.addCommands(commands);
        }
    }

    private void createMessageHandler() {
        mMessageHandler = new ControllerService.OnMessage() {
            @Override
            public void onMessage(Message msg, MessageProto.Message data) {
                switch (msg.what) {
                    case ControllerService.MESSAGE_RECEIVED_MESSAGE:
                        if (data.hasCommand() && data.getCommand().hasReturnValue()) { //we are getting a result from a command
                            processCommandResult();
                        }
                    default:
                        break;
                }
            }
        };
        mActivity.runServiceItem(new Runnable() {
            @Override
            public void run() {
                ControllerService service = UIHelpers.getService(DeviceCommandsFragment.this);
                if (service != null) {
                    service.addMessageHandler(mMessageHandler);
                }
            }
        });

    }

    private void processCommandResult() {
        ScriptCommandProto.ScriptInfo res = mActivity.getService().getLastResult();
        if (res == null)
            return;
        if (res.hasRunFailed() && res.getRunFailed()) {
            UIHelpers.showTextDialog(getActivity(), "Command failed", "The command failed to run on the server\nCheck the script file.", new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {

                }
            });
            return;
        }
        switch (res.getOutputType()) {
            case "list":
            case "text":
                showOutput();
                break;
            case "text_popup":
                showPopup(res.getName(), res.getReturnData());
                break;
            default:
                break;

        }
    }

    private void showPopup(String command, String data) {
        UIHelpers.showTextDialog(getActivity(), command, data, new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                ScriptCommandProto.ScriptInfo res = mActivity.getService().getLastResult();
                if (res.hasPostCommand()) {
                    if ( (!res.getPostCommand().hasCondition()) ||
                            (res.getPostCommand().hasCondition() && res.getReturnValue() == res.getPostCommand().getCondition())) {
                        ScriptCommandProto.ScriptInfo newScript = mActivity.getService().getDeviceCommand(res.getPostCommand().getName());
                        if (newScript != null)
                            runCommand(newScript);
                    }
                }
            }
        });
    }

    private void showOutput() {
        mCommandList.setSelectionAfterHeaderView();
        FragmentOpener op = UIHelpers.findFragmentOpener(this);
        if (op != null) {
            op.openCommandResults();
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
                }
            });
        }
    }

}

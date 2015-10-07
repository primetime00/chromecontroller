package com.kegelapps.chromeboxcontroller;

import android.os.Bundle;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.kegelapps.chromeboxcontroller.proto.MessageProto;
import com.kegelapps.chromeboxcontroller.proto.ScriptCommandProto;

/**
 * Created by keg45397 on 10/6/2015.
 */
public class CommandOutputFragment extends Fragment implements UIHelpers.OnFragmentCancelled {
    private View mRootView;
    private BaseActivity mActivity;
    private ListView mResultList;
    private TextView mResultText;
    private TextView mReturnValue;
    private TextView mCommand;
    private ResultListAdapter mAdapter;

    private ControllerService.OnMessage mMessageHandler;


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.cb_command_result, container, false);
        return mRootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mResultList = (ListView)view.findViewById(R.id.resultList);
        mResultText = (TextView)view.findViewById(R.id.resultText);
        mReturnValue = (TextView)view.findViewById(R.id.returnValue);
        mCommand = (TextView)view.findViewById(R.id.name);
        mAdapter = new ResultListAdapter(getActivity());
        mResultList.setAdapter(mAdapter);
        registerForContextMenu(mResultList);
        mResultList.setVisibility(View.GONE);
        mResultText.setVisibility(View.GONE);

        mActivity = UIHelpers.getBaseActivity(this);

        createMessageHandler();

        mResultList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            }
        });

        mActivity.runServiceItem(new Runnable() {
            @Override
            public void run() {
                Log.d("DeviceInfoFragment", "Grabbing device command list");
                processResult(mActivity.getService().getLastResult());
            }
        });


    }

    private void createMessageHandler() {
        mMessageHandler = new ControllerService.OnMessage() {
            @Override
            public void onMessage(Message msg, MessageProto.Message data) {
                switch (msg.what) {
                    case ControllerService.MESSAGE_RECEIVED_MESSAGE:
                        if (data.hasCommand() && data.getCommand().hasReturnValue()) { //we are getting a result from a sub command
                            processResult(data.getCommand());
                        }
                    default:
                        break;
                }
            }
        };
        mActivity.runServiceItem(new Runnable() {
            @Override
            public void run() {
                ControllerService service = UIHelpers.getService(CommandOutputFragment.this);
                if (service != null) {
                    service.addMessageHandler(mMessageHandler);
                }
            }
        });
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getId() == R.id.resultList) {
            menu.setHeaderTitle("Commands");
            ScriptCommandProto.ScriptInfo res = mActivity.getService().getLastResult();
            for (String cmd : res.getAssociatedCommandList()) {
                menu.add(cmd);
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
        String command = item.getTitle().toString();
        String param = (String)mAdapter.getItem(info.position);
        ScriptCommandProto.ScriptInfo script = ScriptCommandProto.ScriptInfo.newBuilder().setName(command).addParams(param).build();
        MessageProto.Message msg = MessageProto.Message.newBuilder().setCommand(script).build();
        mActivity.getService().sendNetworkMessage(msg);
        return super.onContextItemSelected(item);

    }

    private void processResult(ScriptCommandProto.ScriptInfo result) {
        mCommand.setText(result.getName());
        mReturnValue.setText(String.valueOf(result.getReturnValue()));
        if (result.getOutputType().equals("list")) {
            mAdapter.addResults(result);
            mResultList.setVisibility(View.VISIBLE);
            mResultText.setVisibility(View.GONE);
        }
        else if (result.getOutputType().equals("text")) {
            mResultList.setVisibility(View.GONE);
            mResultText.setVisibility(View.VISIBLE);
            mResultText.setText(result.getReturnData());
        }
        else {
            mResultList.setVisibility(View.GONE);
            mResultText.setVisibility(View.GONE);
        }

    }

    @Override
    public boolean cancel() {
        FragmentOpener op = UIHelpers.findFragmentOpener(this);
        if (op != null) {
            op.popBackStack();
        }
        return false;
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

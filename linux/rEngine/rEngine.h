#pragma once

#include "../rNetwork/rServer.h"
#include "../rNetwork/rTestClient.h"
#include "../rProcessor/rProcessor.h"
#include "Scripts.h"


class rEngine
{
public:
	rEngine(bool server = true);
	~rEngine();

	int run();
	void exit();

	//debug test functions
	void testPing(unsigned int);
	void testInfoRequest(unsigned int);
	void testInfoSet(unsigned int);
	void testCommand();

private:
	bool createServer();
	void createPingClient();

	int dataReceived(rConnection *, sData);
	int connectionEstablished();

	//Client request callback functions
	void onPing(rMessage msg);  //the device got a ping request from the client
	void onInfoRequest(rMessage msg);  //the device got an info request from the client
	void onInfoSet(rMessage msg); //the client requested the device set some information
	void onCommand(rMessage msg); //the client sent a command

	//OS Specific methods
	rProtos::DeviceInfo *populateDeviceInfo();
	void setDeviceInfomation(const rProtos::InfoSet &);

	void readScripts(rProtos::ScriptCommandList &scripts);

	bool convertMessageToVectorData(rMessage);

	boost::shared_ptr<NetworkService> mService;
	boost::shared_ptr<rProcessor> mProcessor;
	boost::shared_ptr<rServer> mServer;

	bool mKill;

	vData mByteData;

	//our device info
	boost::shared_ptr<rProtos::DeviceInfo> mDevInfo;

	//our scripts
    boost::shared_ptr<rScripts> mScripts;

	//debug test members
	boost::shared_ptr<rTestClient> mTestClient;
	bool mIsServer;
};

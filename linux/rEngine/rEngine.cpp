#include <boost/log/trivial.hpp>
#include "rEngine.h"
#include "../rNetwork/rTestClient.h"
#include <boost/bind.hpp>
#include <boost/make_shared.hpp>
#include <boost/filesystem.hpp>
#ifdef __win32__
#include <conio.h>
#endif
#include "AvahiServiceFile.h"
#include "NetInfo.h"

unsigned int testPingCount = 0;

rEngine::rEngine(bool server) : mService(new NetworkService()), mProcessor(new rProcessor()), mIsServer(server)
{
	mProcessor->setMessageFunction(RPROCESSOR_KEY_PING, boost::bind(&rEngine::onPing, this, _1));
	mProcessor->setMessageFunction(RPROCESSOR_KEY_INFOREQUEST, boost::bind(&rEngine::onInfoRequest, this, _1));
	mProcessor->setMessageFunction(RPROCESSOR_KEY_INFOSET, boost::bind(&rEngine::onInfoSet, this, _1));
	mProcessor->setMessageFunction(RPROCESSOR_KEY_COMMAND, boost::bind(&rEngine::onCommand, this, _1));


}

rEngine::~rEngine()
{
}

int rEngine::run()
{
    mKill = false;
	if (mIsServer)
	{
		mServer = boost::make_shared<rServer>(mService);
		if (!createServer())
		{
			BOOST_LOG_TRIVIAL(debug) << "Could not create Remote server! (No mac or ip address found)" ;
			mService->Stop();
			return 1;
		}
	}
	else
	{
		mTestClient = boost::make_shared<rTestClient>(mService);
		createPingClient();
	}

#ifdef __win32__
	while (!_kbhit())
#else
	while (!mKill)
#endif
	{
		mService->Poll();
#ifdef __win32__
		::Sleep(500);
#else
		::usleep(500000);
#endif
	}
    mServer->Shutdown();
	mService->Stop();

	return 0;
}

bool rEngine::createServer()
{
	mDevInfo = boost::shared_ptr<rProtos::DeviceInfo>(populateDeviceInfo());
	std::string mac, ip, mode;
	netinfo::getIfInfo(mac, ip);
	if (mac.length() == 0 || ip.length() == 0)
		netinfo::waitForIP(mac, ip);

    mScripts = boost::make_shared<rScripts>();
    mScripts->readScripts();

    mScripts->runScript("Get Current Mode", mode);


	std::string output;
	if (!mDevInfo) //we don't have a service file, create one!
	{
		mDevInfo = boost::shared_ptr<rProtos::DeviceInfo>(new rProtos::DeviceInfo());
		mDevInfo->set_mac(mac);
		mDevInfo->set_ip(ip);
		if (mScripts->runScript("Get Current Mode", mode))
            mDevInfo->set_mode(mode);

		avahi::createServiceInfoFile("/etc/avahi/services/remotecontrol.service", *mDevInfo);
	}
	if ( (!mDevInfo->has_mac() || mac != mDevInfo->mac()) || (!mDevInfo->has_mode() || mode != mDevInfo->mode()))
	{
		mDevInfo->set_mac(mac);
		mDevInfo->set_ip(ip);
		if (mScripts->runScript("Get Current Mode", mode))
            mDevInfo->set_mode(mode);
		avahi::createServiceInfoFile("/etc/avahi/services/remotecontrol.service", *mDevInfo);
	}
	if (netinfo::isLocalHost())
        mServer->Listen("", 30015);
	else
        mServer->Listen(ip, 30015);
	mServer->SetRecvFunction(boost::bind(&rEngine::dataReceived, this, _1, _2));
	mServer->SetConnectFunction(boost::bind(&rEngine::connectionEstablished, this));
	mServer->AcceptConnection();
	BOOST_LOG_TRIVIAL(debug) << "Created Engine with IP and MAC " << ip << " " << mac ;
	return true;
}

void rEngine::createPingClient()
{
	mTestClient->Connect("127.0.0.1", 30015);
	mTestClient->SetTimeoutSeconds(15);
	mTestClient->AddTimerTimeout("PING", [=] {
		this->testPing(testPingCount++);
	}, 4);

	mTestClient->AddTimerTimeout("INFO", [=] {
		this->testInfoRequest(77);
	}, 2);

/*	mTestClient->AddTimerTimeout("INFOSET", [=] {
		this->testInfoSet(55);
	}, 4);*/
	mTestClient->AddTimerTimeout("COMMAND", [=] {
		this->testCommand();
	}, 4);

}


int rEngine::dataReceived(rConnection *connection, sData data)
{
	mProcessor->insertPacket(data);
	return 0;
}

int rEngine::connectionEstablished()
{
    //we must send the client our info and list of commands
	auto ret = mProcessor->createMessage();
	ret->set_allocated_deviceinfo(populateDeviceInfo());
    ret->set_allocated_commandlist(mScripts->getScriptInfoList());

    rProtos::Display *disp =  new rProtos::Display();
    disp->set_display_mode(rProtos::Display::DISPLAY_DEVICE_INFO);
    ret->set_allocated_display(disp);
	if (convertMessageToVectorData(ret))
		mServer->GetConnection()->Send(mByteData);
	return 0;
}

bool rEngine::convertMessageToVectorData(rMessage msg)
{
	mByteData.resize(msg->ByteSize()+4);
	((unsigned int*)&(mByteData[0]))[0] = msg->ByteSize();
	return msg->SerializePartialToArray(&mByteData[4], msg->ByteSize());
}

void rEngine::onPing(rMessage data)
{
	auto pong = new rProtos::Pong();
	pong->set_id(data->ping().id());
	auto ret = mProcessor->createMessage();
	ret->set_allocated_pong(pong);

	BOOST_LOG_TRIVIAL(debug) << "Got a ping with id " << data->ping().id() <<", sending a pong" ;
	if (convertMessageToVectorData(ret))
		mServer->GetConnection()->Send(mByteData);
}

void rEngine::onInfoRequest(rMessage data)
{
	auto ret = mProcessor->createMessage();
	ret->set_allocated_deviceinfo(populateDeviceInfo());
	BOOST_LOG_TRIVIAL(debug) << "Got a device info request with id " << (data->has_id() ?  (int)data->inforequest().id() : 0) <<", sending device info" ;
	BOOST_LOG_TRIVIAL(debug) << "Device name: " << ret->deviceinfo().name() ;
	if (convertMessageToVectorData(ret))
		mServer->GetConnection()->Send(mByteData);
}

void rEngine::onInfoSet(rMessage data)
{
	setDeviceInfomation(data->infoset());
	auto ret = mProcessor->createMessage();
	ret->set_allocated_deviceinfo(populateDeviceInfo());
	BOOST_LOG_TRIVIAL(debug) << "Got a device info set with id " << (data->has_id() ?  (int)data->inforequest().id() : 0) <<", sending device info" ;
	if (convertMessageToVectorData(ret))
		mServer->GetConnection()->Send(mByteData);
}

void rEngine::onCommand(rMessage data)
{
    rProtos::ScriptInfo *s = new rProtos::ScriptInfo(data->command());
    auto ret = mProcessor->createMessage();
    mScripts->runScript(*s);
    ret->set_allocated_command(s);
    BOOST_LOG_TRIVIAL(debug) << "script type: " << s->output_type() ;
	if (convertMessageToVectorData(ret))
		mServer->GetConnection()->Send(mByteData);
}

rProtos::DeviceInfo *rEngine::populateDeviceInfo()
{
	auto dev = new rProtos::DeviceInfo();
	std::string macAddress, ip;
	//do some linux stuff here!
	if (!avahi::readServiceInfoFile("/etc/avahi/services/remotecontrol.service", *dev))
		return 0;

    netinfo::getIfInfo(macAddress, ip);

	//read ip address
	dev->set_ip(ip);

/*	dev->set_ip("192.168.1.138");
	dev->set_location("Bedroom");
	dev->set_mac("de:ad:be:ef:ba:0d");
	dev->set_id(0);
	dev->set_name("Chromebox 1");
	dev->set_mode("Steam");*/
	return dev; //or null
}

void rEngine::setDeviceInfomation(const rProtos::InfoSet &info)
{
	avahi::updateServiceInfoFile("/etc/avahi/services/remotecontrol.service", info);
}

void rEngine::testPing(unsigned int id)
{
	auto ping = new rProtos::Ping();
	ping->set_id(id);
	auto ret = mProcessor->createMessage(64);
	ret->set_allocated_ping(ping);
	BOOST_LOG_TRIVIAL(debug) << "Sending a ping with id " << id ;
	if (convertMessageToVectorData(ret))
		mTestClient->Send(mByteData);
}

void rEngine::testInfoRequest(unsigned int id)
{
	auto request = new rProtos::InfoRequest();
	request->set_id(id);
	auto ret = mProcessor->createMessage(67);
	ret->set_allocated_inforequest(request);
	BOOST_LOG_TRIVIAL(debug) << "Sending info request with id " << id ;
	if (convertMessageToVectorData(ret))
		mTestClient->Send(mByteData);
}

void rEngine::testInfoSet(unsigned int id)
{
	auto infoset = new rProtos::InfoSet();
	infoset->set_id(id);
	infoset->set_name("RyanBox");
	infoset->set_location("Bathroom");
	auto ret = mProcessor->createMessage(99);
	ret->set_allocated_infoset(infoset);
	BOOST_LOG_TRIVIAL(debug) << "Sending info set with id " << id ;
	if (convertMessageToVectorData(ret))
		mTestClient->Send(mByteData);
}

void rEngine::testCommand()
{
	rProtos::ScriptInfo *cmd = new rProtos::ScriptInfo();
	cmd->set_name("Process Lister");
	auto ret = mProcessor->createMessage(33);
	ret->set_allocated_command(cmd);
	BOOST_LOG_TRIVIAL(debug) << "Sending command: " << cmd->name() ;
	if (convertMessageToVectorData(ret))
		mTestClient->Send(mByteData);
}

void rEngine::exit()
{
    mKill = true;
}


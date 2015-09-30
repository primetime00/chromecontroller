#include "rTestClient.h"
#include <iostream>
#include <boost/thread/mutex.hpp>
#include <boost/make_shared.hpp>
#include <boost/bind.hpp>
#include <iomanip>

#include "Message.pb.h"

boost::mutex client_global_stream_lock;

void rTestClient::OnAccept(const std::string & host, uint16_t port)
{
	// Start the next receive
	Recv();
}


void rTestClient::OnConnect(const std::string & host, uint16_t port)
{
	totalTime = 0;
	isConnected = true;
	Recv();

}

void rTestClient::OnSend(const std::vector< uint8_t > & buffer)
{
	client_global_stream_lock.lock();
	std::cout << "Sending data..." << std::endl;
	client_global_stream_lock.unlock();
}

void rTestClient::OnRecv(std::vector< uint8_t > & buffer)
{
	ResetTimeoutTimer();
	client_global_stream_lock.lock();
	std::cout << "Received " << buffer.size() << " of data..." << std::endl;
	client_global_stream_lock.unlock();

	while (buffer.size() > 4)
	{
		auto msg = new rProtos::Message();
		unsigned int size = ((unsigned int*)buffer.data())[0];
		bool res = msg->ParseFromArray(buffer.data()+4, size);

		client_global_stream_lock.lock();
		if (msg->has_pong())
			std::cout << "Message is a pong!" << std::endl;
		if (msg->has_deviceinfo())
		{
			std::cout << "Message is a device info!" << std::endl;
			std::cout << "IP: " << msg->deviceinfo().ip() << std::endl;
			std::cout << "MAC: " << msg->deviceinfo().mac() << std::endl;
			if (msg->deviceinfo().has_name())
				std::cout << "Name: " << msg->deviceinfo().name() << std::endl;
		}
		if (msg->has_command())
		{
			std::cout << "Ran command [" << msg->command().name() << "]" << std::endl;
			if (msg->command().run_failed())
                std::cout << "This command failed to run on the server!" << std::endl;
            else
            {
                if (msg->command().has_return_data() && msg->command().return_data().length() > 0)
                    std::cout << "Output data:\n" << msg->command().return_data() << std::endl;
                if (msg->command().has_return_value())
                    std::cout << "Return value: " << msg->command().return_value() << std::endl;
            }
		}
		if (msg->has_commandlist())
		{
            std::cout << "Received command list that has " << msg->commandlist().scripts_size() << " commands." << std::endl;
		}
		client_global_stream_lock.unlock();

		buffer.erase(buffer.begin(), buffer.begin()+size+4);
		delete msg;
	}


	// Start the next receive

	Recv();
}

void rTestClient::OnTimer(const boost::posix_time::time_duration & delta)
{
	if (CheckTimeout())
	{
		std::cout << "client disconnect due to inactivity" << std::endl;
		Disconnect();
	}
	if (isConnected)
	{
		for (auto i : m_timers)
		{
			i.second->totalTime += (delta.total_milliseconds() / 1000.0f);
			if (i.second->totalTime >= (float) i.second->timeoutSeconds && !i.second->timeOutFunction.empty())
			{
				i.second->timeOutFunction();
				i.second->totalTime = 0.0f;
			}
		}
	}

}

void rTestClient::OnError(const boost::system::error_code & error)
{
	client_global_stream_lock.lock();
	std::cout << "rTestClient had an error " << error << std::endl;
	client_global_stream_lock.unlock();
}

void rTestClient::OnDisconnect()
{
	client_global_stream_lock.lock();
	std::cout << "Connection disconnected!" << std::endl;
	client_global_stream_lock.unlock();
}

bool rTestClient::CheckTimeout() {
	if (GetTimeoutSeconds() == 0)
		return false;
	auto tm = boost::posix_time::microsec_clock::local_time();
	if (m_last_ping_time == boost::posix_time::not_a_date_time)
		m_last_ping_time = tm;
	if ( (tm - m_last_ping_time).total_seconds() > GetTimeoutSeconds())
	{
		client_global_stream_lock.lock();
		std::cout << GetTimeoutSeconds() << " seconds have passed.  assuming client disconnected!" << std::endl;
		client_global_stream_lock.unlock();
		return true;
	}
	return false;
}

void rTestClient::ResetTimeoutTimer() {
	m_last_ping_time = boost::posix_time::microsec_clock::local_time();
}

rTestClient::rTestClient(boost::shared_ptr< NetworkService > service)
	: NetworkConnection(service), m_timeoutSeconds(5), isConnected(false)
{
}

rTestClient::~rTestClient()
{
}

void rTestClient::SetTimeoutSeconds(unsigned int s) { m_timeoutSeconds = s; }

unsigned int rTestClient::GetTimeoutSeconds() { return m_timeoutSeconds;}

void rTestClient::Ping() {
	char data[4];
	std::string val = "hello ryan, how are you doing today?  Good, well that is great!";
	((unsigned int*)&data)[0] = val.size();
	std::string sizeStr(data, data+4);
	std::string res = sizeStr+val;
	val = "COOL MAN";
	((unsigned int*)&data)[0] = val.size();
	sizeStr = std::string(data, data+4);
	res= res+sizeStr;
	res= res+val;

	sendBuffer.assign(res.begin(), res.end());
	Send(sendBuffer);
}

void rTestClient::addTimerTimeout(std::string name, boost::function<void()> func, unsigned int seconds)
{
	auto timer = boost::make_shared<rTestTimer>(func, seconds);
	m_timers[name] = timer;
}
void rTestClient::removeTimerTimeout(std::string name)
{
	if (m_timers.count(name) > 0)
		m_timers.erase(name);
}

void rTestClient::AddTimerTimeout(std::string name, boost::function<void()> func, unsigned int seconds)
{
	auto _this = boost::static_pointer_cast<rTestClient>(shared_from_this());
	GetStrand().post(boost::bind(&rTestClient::addTimerTimeout, _this, name, func, seconds));
}
void rTestClient::RemoveTimerTimeout(std::string name)
{
	auto _this = boost::static_pointer_cast<rTestClient>(shared_from_this());
	GetStrand().post(boost::bind(&rTestClient::removeTimerTimeout, _this, name));
}

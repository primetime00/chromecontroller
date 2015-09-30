#include "rServer.h"
#include <iostream>
#include <boost/thread/mutex.hpp>
#include <boost/make_shared.hpp>
#include <boost/bind.hpp>
#include <iomanip>


boost::mutex global_stream_lock;

bool rServer::OnAccept(boost::shared_ptr< NetworkConnection > connection, const std::string & host, uint16_t port)
{
	global_stream_lock.lock();
	std::cout << "Accepted a new connection from " << host << ":" << port << std::endl;
	global_stream_lock.unlock();
    if (!m_connectFunction.empty())
    {
        m_connectFunction();
    }


	return true;
}

void rServer::OnTimer(const boost::posix_time::time_duration & delta)
{
}

void rServer::OnError(const boost::system::error_code & error)
{
	global_stream_lock.lock();
	std::cout << "rServer had an error: " << error << std::endl;
	global_stream_lock.unlock();
}

void rServer::OnClientDisconnect()
{
	boost::static_pointer_cast<rServer>(shared_from_this())->AcceptConnection();
}

rServer::rServer(boost::shared_ptr< NetworkService > service)
	: ServerAcceptor(service)
{
}

rServer::~rServer()
{
}

void rServer::AcceptConnection()
{
	if (m_connection)
		m_connection.reset();
	m_connection = boost::make_shared<rConnection>(GetService());
	m_connection->SetRecvFunction(m_recvFunction);
	m_connection->SetConnectFunction(m_connectFunction);
	Accept(m_connection);
}

void rConnection::OnAccept(const std::string & host, uint16_t port)
{
	// Start the next receive
	Recv();
}

void rConnection::OnConnect(const std::string & host, uint16_t port)
{
	Recv();
}

void rConnection::OnSend(const std::vector< uint8_t > & buffer)
{
	global_stream_lock.lock();
	std::cout << "Sending data..." << std::endl;
	global_stream_lock.unlock();
}

void rConnection::OnRecv(std::vector< uint8_t > & buffer)
{
	global_stream_lock.lock();
	std::cout << "Received " << buffer.size() << " of data..." << std::endl;
	global_stream_lock.unlock();

	unsigned int packetSize;
	m_oldData.insert(m_oldData.end(), buffer.begin(), buffer.end());
	ResetTimeoutTimer();
	while (m_oldData.size() >= 4)
	{
		packetSize =  ntohl((m_oldData[0] << 24) |(m_oldData[1] << 16) | (m_oldData[2] << 8) | m_oldData[3]);
		if ((m_oldData.size()-4) >= packetSize)  //tear out a packet!
		{
			boost::shared_ptr<std::vector<uint8_t>> data = boost::make_shared<std::vector<uint8_t>>(m_oldData.begin()+4, m_oldData.begin()+4+packetSize);
			if (!m_recvFunction.empty())
			{
				m_recvFunction(this, data);
			}
			m_oldData.erase(m_oldData.begin(), m_oldData.begin()+4+packetSize);
		}
		else //not big enough for a packet
		{
			break;
		}
	}
	ResetTimeoutTimer();

	// Start the next receive
	Recv();
}

void rConnection::OnTimer(const boost::posix_time::time_duration & delta)
{
	if (CheckTimeout())
		Disconnect();
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

void rConnection::OnError(const boost::system::error_code & error)
{
	global_stream_lock.lock();
	std::cout << "rConnection had an error " << error << std::endl;
	global_stream_lock.unlock();
}

void rConnection::OnDisconnect()
{
	global_stream_lock.lock();
	std::cout << "Connection disconnected!" << std::endl;
	global_stream_lock.unlock();
}

void rConnection::SetInputProcessFunction(boost::function<void()> func)
{
	m_processInputFunc = func;
}


bool rConnection::CheckTimeout() {
	if (GetTimeoutSeconds() == 0)
		return false;
	auto tm = boost::posix_time::microsec_clock::local_time();
	if (m_last_ping_time == boost::posix_time::not_a_date_time)
		m_last_ping_time = tm;
	if ( (tm - m_last_ping_time).total_seconds() > GetTimeoutSeconds())
	{
		global_stream_lock.lock();
		std::cout << GetTimeoutSeconds() << " seconds have passed.  assuming client disconnected!" << std::endl;
		global_stream_lock.unlock();
		return true;
	}
	return false;
}

void rConnection::ResetTimeoutTimer() {
	m_last_ping_time = boost::posix_time::microsec_clock::local_time();
}

rConnection::rConnection(boost::shared_ptr< NetworkService > service)
	: NetworkConnection(service), m_timeoutSeconds(5)
{
}

rConnection::~rConnection()
{
}

void rConnection::SetTimeoutSeconds(unsigned int s) { m_timeoutSeconds = s; }

unsigned int rConnection::GetTimeoutSeconds() { return m_timeoutSeconds;}

void rConnection::addTimerTimeout(std::string name, boost::function<void()> func, unsigned int seconds)
{
	auto timer = boost::make_shared<rTimer>(func, seconds);
	m_timers[name] = timer;
}
void rConnection::removeTimerTimeout(std::string name)
{
	if (m_timers.count(name) > 0)
		m_timers.erase(name);
}

void rConnection::AddTimerTimeout(std::string name, boost::function<void()> func, unsigned int seconds)
{
	auto _this = boost::static_pointer_cast<rConnection>(shared_from_this());
	GetStrand().post(boost::bind(&rConnection::addTimerTimeout, _this, name, func, seconds));
}
void rConnection::RemoveTimerTimeout(std::string name)
{
	auto _this = boost::static_pointer_cast<rConnection>(shared_from_this());
	GetStrand().post(boost::bind(&rConnection::removeTimerTimeout, _this, name));
}





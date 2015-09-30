#pragma once

#include "NetworkConnection.h"
#include <boost/thread/mutex.hpp>
#include <boost/function.hpp>
#include <boost/unordered_map.hpp>


extern boost::mutex client_global_stream_lock2;

struct rTestTimer 
{
	boost::function<void()> timeOutFunction;
	unsigned int timeoutSeconds;
	float totalTime;
	rTestTimer(boost::function<void()> f, unsigned int t)
	{
		timeOutFunction = f;
		timeoutSeconds = t;
		totalTime = 0.0f;
	}
};



class rTestClient : public NetworkConnection
{
private:
	boost::posix_time::ptime m_last_ping_time;
	bool isConnected;
	long totalTime;
	unsigned int m_timeoutSeconds;
	boost::function<void()> connectFunc;
	boost::function<void()> recvFunc;
	std::vector< uint8_t > sendBuffer;
	boost::unordered_map<std::string, boost::shared_ptr<rTestTimer>> m_timers;

private:
	void OnAccept(const std::string & host, uint16_t port);
	void OnConnect(const std::string & host, uint16_t port);
	void OnSend(const std::vector< uint8_t > & buffer);
	void OnRecv(std::vector< uint8_t > & buffer);
	void OnTimer(const boost::posix_time::time_duration & delta);
	void OnError(const boost::system::error_code & error);
	void OnDisconnect();
	bool CheckTimeout();
	void ResetTimeoutTimer();

	void addTimerTimeout(std::string name, boost::function<void()>, unsigned int seconds);
	void removeTimerTimeout(std::string name);

public:
	rTestClient(boost::shared_ptr< NetworkService > service);
	~rTestClient();
	void SetTimeoutSeconds(unsigned int s);
	unsigned int GetTimeoutSeconds();

	void setConnectFunction(boost::function<void()> f) { connectFunc = f; }
	void setRecvFunction(boost::function<void()> f) { recvFunc = f; }

	void AddTimerTimeout(std::string name, boost::function<void()>, unsigned int seconds);
	void RemoveTimerTimeout(std::string name);

	void Ping();

};
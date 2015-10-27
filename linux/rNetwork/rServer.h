#pragma once
#include "ServerAcceptor.h"
#include "NetworkConnection.h"
#include <boost/thread/mutex.hpp>
#include <boost/function.hpp>
#include <vector>
#include <boost/unordered_map.hpp>

class rConnection;


extern boost::mutex global_stream_lock;

struct rTimer
{
	boost::function<void()> timeOutFunction;
	unsigned int timeoutSeconds;
	float totalTime;
	rTimer(boost::function<void()> f, unsigned int t)
	{
		timeOutFunction = f;
		timeoutSeconds = t;
		totalTime = 0.0f;
	}
};

typedef boost::shared_ptr<rConnection> rConnectionPtr;
typedef boost::shared_ptr<rTimer> rTimerPtr;


class rServer : public ServerAcceptor
{
private:
	rConnectionPtr m_connection;
	boost::function<int(rConnection*, boost::shared_ptr<std::vector<uint8_t>>)> m_recvFunction;
	boost::function<int()> m_connectFunction;
	bool m_serverShutdown;

private:
	bool OnAccept(boost::shared_ptr< NetworkConnection > connection, const std::string & host, uint16_t port);
	void OnTimer(const boost::posix_time::time_duration & delta);
	void OnError(const boost::system::error_code & error);
	void OnClientDisconnect();

public:
	rServer(boost::shared_ptr< NetworkService > service);
	~rServer();
	void AcceptConnection();
	void SetRecvFunction(boost::function<int(rConnection*, boost::shared_ptr<std::vector<uint8_t>>)> f) { m_recvFunction = f; }
	void SetConnectFunction(boost::function<int()> f) { m_connectFunction = f; }
	void Shutdown();
	rConnectionPtr GetConnection() { return m_connection; }

};

class rConnection : public NetworkConnection
{
private:
	boost::posix_time::ptime m_last_ping_time;
	boost::function<void()> m_processInputFunc;
	boost::function<int(rConnection*, boost::shared_ptr<std::vector<uint8_t>>)> m_recvFunction;
	boost::function<int()> m_connectFunction;
	unsigned int m_timeoutSeconds;
	std::vector<uint8_t> m_oldData;
	unsigned int m_originalSize;
	boost::unordered_map<std::string, rTimerPtr> m_timers;

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
	rConnection(boost::shared_ptr< NetworkService > service);
	~rConnection();
	void SetInputProcessFunction(boost::function<void()> func);
	void SetTimeoutSeconds(unsigned int s);
	unsigned int GetTimeoutSeconds();

	void AddTimerTimeout(std::string name, boost::function<void()>, unsigned int seconds);
	void RemoveTimerTimeout(std::string name);


	void SetRecvFunction(boost::function<int(rConnection*, boost::shared_ptr<std::vector<uint8_t>>)> f) {
		m_recvFunction = f;
	}
    void SetConnectFunction(boost::function<int()> f) { m_connectFunction = f; }
    void Shutdown();



};

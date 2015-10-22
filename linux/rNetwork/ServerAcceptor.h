#pragma once
#include <boost/enable_shared_from_this.hpp>
#include <boost/asio.hpp>
#include <boost/function.hpp>
#include "NetworkService.h"
#include "NetworkConnection.h"

class NetworkConnection;

class ServerAcceptor : public boost::enable_shared_from_this< ServerAcceptor >
{
	friend class NetworkService;

private:
	boost::shared_ptr< NetworkService > m_service;
	boost::asio::ip::tcp::acceptor m_acceptor;
	boost::asio::strand m_io_strand;
	boost::asio::deadline_timer m_timer;
	boost::posix_time::ptime m_last_time;
	int32_t m_timer_interval;
	volatile uint32_t m_error_state;

private:
	ServerAcceptor(const ServerAcceptor & rhs);
	ServerAcceptor & operator =(const ServerAcceptor & rhs);

	void StartTimer();
	void StartError(const boost::system::error_code & error);
	void DispatchAccept(boost::shared_ptr< NetworkConnection > connection);
	void HandleTimer(const boost::system::error_code & error);
	void HandleAccept(const boost::system::error_code & error, boost::shared_ptr< NetworkConnection > connection);
	void HandleClientDisconnect();

protected:
	ServerAcceptor(boost::shared_ptr< NetworkService > service);
	virtual ~ServerAcceptor();

	template <typename Derived>
	std::shared_ptr<Derived> shared_from_base();

private:
	// Called when a connection has connected to the server. This function
	// should return true to invoke the connection's OnAccept function if the
	// connection will be kept. If the connection will not be kept, the
	// connection's Disconnect function should be called and the function
	// should return false.
	virtual bool OnAccept(boost::shared_ptr< NetworkConnection > connection, const std::string & host, uint16_t port) = 0;

	// Called on each timer event.
	virtual void OnTimer(const boost::posix_time::time_duration & delta) = 0;

	// Called when an error is encountered. Most typically, this is when the
	// ServerAcceptor is being closed via the Stop function or if the Listen is
	// called on an address that is not available.
	virtual void OnError(const boost::system::error_code & error) = 0;

	// When a client disconnects
	virtual void OnClientDisconnect() = 0;

public:
	// Returns the NetworkService object.
	boost::shared_ptr< NetworkService > GetService();

	// Returns the ServerAcceptor object.
	boost::asio::ip::tcp::acceptor & GetServerAcceptor();

	// Returns the strand object.
	boost::asio::strand & GetStrand();

	// Sets the timer interval of the object. The interval is changed after
	// the next update is called. The default value is 1000 ms.
	void SetTimerInterval(int32_t timer_interval_ms);

	// Returns the timer interval of the object.
	int32_t GetTimerInterval() const;

	// Returns true if this object has an error associated with it.
	bool HasError();

public:
	// Begin listening on the specific network interface.
	void Listen(const std::string & host, const uint16_t & port);

	// Posts the connection to the listening interface. The next client that
	// connections will be given this connection. If multiple calls to Accept
	// are called at a time, then they are accepted in a FIFO order.
	void Accept(boost::shared_ptr< NetworkConnection > connection);

	// Stop the ServerAcceptor from listening.
	void Stop();

	virtual void Shutdown();
};


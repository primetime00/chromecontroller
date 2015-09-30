#include "ServerAcceptor.h"
#include "NetworkConnection.h"
#include <boost/lexical_cast.hpp>
#include <boost/bind.hpp>
#include <boost/interprocess/detail/atomic.hpp>
#include <iostream>

ServerAcceptor::ServerAcceptor(boost::shared_ptr< NetworkService > service)
	: m_service(service), m_acceptor(service->GetService()), m_io_strand(service->GetService()), m_timer(service->GetService()), m_timer_interval(1000), m_error_state(0)
{
}

ServerAcceptor::~ServerAcceptor()
{
}

void ServerAcceptor::StartTimer()
{
	m_last_time = boost::posix_time::microsec_clock::local_time();
	m_timer.expires_from_now(boost::posix_time::milliseconds(m_timer_interval));
	m_timer.async_wait(m_io_strand.wrap(boost::bind(&ServerAcceptor::HandleTimer, shared_from_this(), _1)));
}

void ServerAcceptor::StartError(const boost::system::error_code & error)
{
	if (boost::interprocess::ipcdetail::atomic_cas32(&m_error_state, 1, 0) == 0)
	{
		boost::system::error_code ec;
		m_acceptor.cancel(ec);
		m_acceptor.close(ec);
		m_timer.cancel(ec);
		OnError(error);
	}
}

void ServerAcceptor::DispatchAccept(boost::shared_ptr< NetworkConnection > connection)
{
	m_acceptor.async_accept(connection->GetSocket(), connection->GetStrand().wrap(boost::bind(&ServerAcceptor::HandleAccept, shared_from_this(), _1, connection)));
}

void ServerAcceptor::HandleTimer(const boost::system::error_code & error)
{
	if (error || HasError() || m_service->HasStopped())
	{
		StartError(error);
	}
	else
	{
		OnTimer(boost::posix_time::microsec_clock::local_time() - m_last_time);
		StartTimer();
	}
}

void ServerAcceptor::HandleAccept(const boost::system::error_code & error, boost::shared_ptr< NetworkConnection > connection)
{
	if (error || HasError() || m_service->HasStopped())
	{
		connection->StartError(error);
	}
	else
	{
		if (connection->GetSocket().is_open())
		{
			connection->SetDisconnectCallback(boost::bind(&ServerAcceptor::HandleClientDisconnect, shared_from_this()));
			connection->StartTimer();
			if (OnAccept(connection, connection->GetSocket().remote_endpoint().address().to_string(), connection->GetSocket().remote_endpoint().port()))
			{
				connection->OnAccept(m_acceptor.local_endpoint().address().to_string(), m_acceptor.local_endpoint().port());
			}
		}
		else
		{
			StartError(error);
		}
	}
}

void ServerAcceptor::HandleClientDisconnect()
{
	OnClientDisconnect();
}

void ServerAcceptor::Stop()
{
	m_io_strand.post(boost::bind(&ServerAcceptor::HandleTimer, shared_from_this(), boost::asio::error::connection_reset));
}

void ServerAcceptor::Accept(boost::shared_ptr< NetworkConnection > connection)
{
	m_io_strand.post(boost::bind(&ServerAcceptor::DispatchAccept, shared_from_this(), connection));
}

void ServerAcceptor::Listen(const std::string & host, const uint16_t & port)
{
	boost::asio::ip::tcp::endpoint endpoint;
	if (!host.empty())
	{
		boost::asio::ip::tcp::resolver resolver(m_service->GetService());
		boost::asio::ip::tcp::resolver::query query(host, boost::lexical_cast< std::string >(port));
		endpoint = *resolver.resolve(query);
	}
	else
		endpoint = boost::asio::ip::tcp::endpoint(boost::asio::ip::address_v4::any(), port);
	m_acceptor.open(endpoint.protocol());
	m_acceptor.set_option(boost::asio::ip::tcp::acceptor::reuse_address(false));
	m_acceptor.bind(endpoint);
	m_acceptor.listen(boost::asio::socket_base::max_connections);
	StartTimer();
}

boost::shared_ptr< NetworkService > ServerAcceptor::GetService()
{
	return m_service;
}

boost::asio::ip::tcp::acceptor & ServerAcceptor::GetServerAcceptor()
{
	return m_acceptor;
}

boost::asio::strand & ServerAcceptor::GetStrand()
{
	return m_io_strand;
}

int32_t ServerAcceptor::GetTimerInterval() const
{
	return m_timer_interval;
}

void ServerAcceptor::SetTimerInterval(int32_t timer_interval)
{
	m_timer_interval = timer_interval;
}

bool ServerAcceptor::HasError()
{
	return (boost::interprocess::ipcdetail::atomic_cas32(&m_error_state, 1, 1) == 1);
}

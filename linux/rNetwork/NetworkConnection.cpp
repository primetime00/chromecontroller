#include "NetworkConnection.h"
#include <boost/bind.hpp>
#include <boost/interprocess/detail/atomic.hpp>
#include <boost/lexical_cast.hpp>

NetworkConnection::NetworkConnection(boost::shared_ptr< NetworkService > service)
	: m_service(service), m_socket(service->GetService()), m_io_strand(service->GetService()), m_timer(service->GetService()), m_receive_buffer_size(4096), m_timer_interval(1000), m_error_state(0)
{
}

NetworkConnection::~NetworkConnection()
{
}

void NetworkConnection::Bind(const std::string & ip, uint16_t port)
{
	boost::asio::ip::tcp::endpoint endpoint(boost::asio::ip::address::from_string(ip), port);
	m_socket.open(endpoint.protocol());
	m_socket.set_option(boost::asio::ip::tcp::acceptor::reuse_address(false));
	m_socket.bind(endpoint);
}

void NetworkConnection::StartSend()
{
	if (!m_pending_sends.empty())
	{
		boost::asio::async_write(m_socket, boost::asio::buffer(m_pending_sends.front()), m_io_strand.wrap(boost::bind(&NetworkConnection::HandleSend, shared_from_this(), boost::asio::placeholders::error, m_pending_sends.begin())));
	}
}

void NetworkConnection::StartRecv(int32_t total_bytes)
{
	if (total_bytes > 0)
	{
		m_recv_buffer.resize(total_bytes);
		boost::asio::async_read(m_socket, boost::asio::buffer(m_recv_buffer), m_io_strand.wrap(boost::bind(&NetworkConnection::HandleRecv, shared_from_this(), _1, _2)));
	}
	else
	{
		m_recv_buffer.resize(m_receive_buffer_size);
		m_socket.async_read_some(boost::asio::buffer(m_recv_buffer), m_io_strand.wrap(boost::bind(&NetworkConnection::HandleRecv, shared_from_this(), _1, _2)));
	}
}

void NetworkConnection::StartTimer()
{
	m_last_time = boost::posix_time::microsec_clock::local_time();
	m_timer.expires_from_now(boost::posix_time::milliseconds(m_timer_interval));
	m_timer.async_wait(m_io_strand.wrap(boost::bind(&NetworkConnection::DispatchTimer, shared_from_this(), _1)));
}

void NetworkConnection::StartError(const boost::system::error_code & error)
{
	if (boost::interprocess::ipcdetail::atomic_cas32(&m_error_state, 1, 0) == 0)
	{
		boost::system::error_code ec;
		m_socket.shutdown(boost::asio::ip::tcp::socket::shutdown_both, ec);
		m_socket.close(ec);
		m_timer.cancel(ec);
		if (error.value() != 995) //operation aborted (disconnect)
			OnError(error);
	}
}

void NetworkConnection::StartDisconnect()
{
	boost::system::error_code ec;
	m_socket.shutdown(boost::asio::ip::tcp::socket::shutdown_both, ec);
	m_socket.close(ec);
}

void NetworkConnection::HandleConnect(const boost::system::error_code & error)
{
	if (error || HasError() || m_service->HasStopped())
	{
		StartError(error);
	}
	else
	{
		if (m_socket.is_open())
		{
			OnConnect(m_socket.remote_endpoint().address().to_string(), m_socket.remote_endpoint().port());
		}
		else
		{
			StartError(error);
		}
	}
}

void NetworkConnection::HandleSend(const boost::system::error_code &  error, std::list< std::vector< uint8_t > >::iterator itr)
{
	if (error || HasError() || m_service->HasStopped())
	{
		StartError(error);
	}
	else
	{
		OnSend(*itr);
		m_pending_sends.erase(itr);
		StartSend();
	}
}

void NetworkConnection::HandleRecv(const boost::system::error_code & error, int32_t actual_bytes)
{
	if (error || HasError() || m_service->HasStopped())
	{
		StartError(error);
	}
	else
	{
		m_recv_buffer.resize(actual_bytes);
		OnRecv(m_recv_buffer);
		m_pending_recvs.pop_front();
		if (!m_pending_recvs.empty())
		{
			StartRecv(m_pending_recvs.front());
		}
	}
}

void NetworkConnection::HandleTimer(const boost::system::error_code & error)
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

void NetworkConnection::HandleDisconnect(const boost::system::error_code & error)
{
	if (error == boost::asio::error::connection_reset) 
	{
		if (!m_disconnect_callback.empty())
			GetStrand().post(m_disconnect_callback);
		StartDisconnect();
		OnDisconnect();
	}
	else 
	{
		StartError(error);
	}
}

void NetworkConnection::DispatchSend(std::vector< uint8_t > buffer)
{
	bool should_start_send = m_pending_sends.empty();
	m_pending_sends.push_back(buffer);
	if (should_start_send)
	{
		StartSend();
	}
}

void NetworkConnection::DispatchRecv(int32_t total_bytes)
{
	bool should_start_receive = m_pending_recvs.empty();
	m_pending_recvs.push_back(total_bytes);
	if (should_start_receive)
	{
		StartRecv(total_bytes);
	}
}

void NetworkConnection::DispatchTimer(const boost::system::error_code & error)
{
	m_io_strand.post(boost::bind(&NetworkConnection::HandleTimer, shared_from_this(), error));
}

void NetworkConnection::Connect(const std::string & host, uint16_t port)
{
	boost::system::error_code ec;
	boost::asio::ip::tcp::resolver resolver(m_service->GetService());
	boost::asio::ip::tcp::resolver::query query(host, boost::lexical_cast< std::string >(port));
	boost::asio::ip::tcp::resolver::iterator iterator = resolver.resolve(query);
	m_socket.async_connect(*iterator, m_io_strand.wrap(boost::bind(&NetworkConnection::HandleConnect, shared_from_this(), _1)));
	StartTimer();
}

void NetworkConnection::Disconnect()
{
	m_io_strand.post(boost::bind(&NetworkConnection::HandleDisconnect, shared_from_this(), boost::asio::error::connection_reset));
}

void NetworkConnection::SetDisconnectCallback(boost::function<void()> func)
{
	m_disconnect_callback = func;
}

void NetworkConnection::Recv(int32_t total_bytes)
{
	m_io_strand.post(boost::bind(&NetworkConnection::DispatchRecv, shared_from_this(), total_bytes));
}

void NetworkConnection::Send(const std::vector< uint8_t > & buffer)
{
	m_io_strand.post(boost::bind(&NetworkConnection::DispatchSend, shared_from_this(), buffer));
}

boost::asio::ip::tcp::socket & NetworkConnection::GetSocket()
{
	return m_socket;
}

boost::asio::strand & NetworkConnection::GetStrand()
{
	return m_io_strand;
}

boost::shared_ptr< NetworkService > NetworkConnection::GetService()
{
	return m_service;
}

void NetworkConnection::SetReceiveBufferSize(int32_t size)
{
	m_receive_buffer_size = size;
}

int32_t NetworkConnection::GetReceiveBufferSize() const
{
	return m_receive_buffer_size;
}

int32_t NetworkConnection::GetTimerInterval() const
{
	return m_timer_interval;
}

void NetworkConnection::SetTimerInterval(int32_t timer_interval)
{
	m_timer_interval = timer_interval;
}

bool NetworkConnection::HasError()
{
	return (boost::interprocess::ipcdetail::atomic_cas32(&m_error_state, 1, 1) == 1);
}

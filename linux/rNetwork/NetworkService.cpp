#include "NetworkService.h"
#include <boost/interprocess/detail/atomic.hpp>

NetworkService::NetworkService()
	: m_work_ptr(new boost::asio::io_service::work(m_io_service)), m_shutdown(0)
{
}

NetworkService::~NetworkService()
{
}

boost::asio::io_service & NetworkService::GetService()
{
	return m_io_service;
}

bool NetworkService::HasStopped()
{
	return (boost::interprocess::ipcdetail::atomic_cas32(&m_shutdown, 1, 1) == 1);
}

void NetworkService::Poll()
{
	m_io_service.poll();
}

void NetworkService::Run()
{
	m_io_service.run();
}

void NetworkService::Stop()
{
	if (boost::interprocess::ipcdetail::atomic_cas32(&m_shutdown, 1, 0) == 0)
	{
		m_work_ptr.reset();
		m_io_service.run();
		m_io_service.stop();
	}
}

void NetworkService::Reset()
{
	if (boost::interprocess::ipcdetail::atomic_cas32(&m_shutdown, 0, 1) == 1)
	{
		m_io_service.reset();
		m_work_ptr.reset(new boost::asio::io_service::work(m_io_service));
	}
}
#pragma once

#include <boost/shared_ptr.hpp>
#include <boost/unordered_map.hpp>
#include "DeviceInfo.pb.h"
#include "InfoSet.pb.h"

namespace avahi
{
	enum {
		NAME,
		MAC,
		MODE,
		LOCATION
	};

	void readServiceInfo(std::string &data, rProtos::DeviceInfo &record);
	bool readServiceInfoFile(const std::string &fname, rProtos::DeviceInfo &record);
	void updateServiceInfo(std::string &data, const rProtos::InfoSet &record);
	void updateServiceInfoFile(const std::string &fname, const rProtos::InfoSet &record);
	void createServiceInfo(std::string &data, const rProtos::DeviceInfo &record);
	void createServiceInfoFile(const std::string &fname, const rProtos::DeviceInfo &record);

	std::string getData();
	std::string getFakeData();
};

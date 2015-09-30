#pragma once

#include <vector>
#include <boost/shared_ptr.hpp>

#include "Message.pb.h"
#include "Pong.pb.h"
#include "DeviceInfo.pb.h"

typedef std::vector<uint8_t> vData;
typedef boost::shared_ptr<vData> sData;
typedef boost::shared_ptr<rProtos::Message> rMessage;
typedef boost::shared_ptr<rProtos::Pong> rPong;
typedef boost::shared_ptr<rProtos::DeviceInfo> rDeviceInfo;

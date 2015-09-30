#pragma once

#include <boost/shared_ptr.hpp>
#include <boost/function.hpp>
#include <boost/unordered_map.hpp>

#include <vector>
#include "Message.pb.h"
#include "Keys.h"
#include "Types.h"

class rProcessor
{
public:
	rProcessor();
	~rProcessor();

	void insertPacket(sData data);
	void setMessageReadyFunction(boost::function<void(rMessage)> func) { mMessageReadyFunction = func; }
	rMessage createMessage(unsigned int id=0);

	void setMessageFunction(std::string name, boost::function<void(rMessage)> function);

private:

	bool executeMessage(std::string name, rMessage data);

	boost::function<void(rMessage)> mMessageReadyFunction;

	boost::unordered_map<std::string, boost::function<void(rMessage)>> mFunctionMap;

};
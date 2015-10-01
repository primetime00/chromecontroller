#include <iostream>
#include "rProcessor.h"
#include "Keys.h"

rProcessor::rProcessor()
{
}

rProcessor::~rProcessor()
{
}

void rProcessor::insertPacket(sData data)
{
	if (!data)
		return;
	auto m = rMessage(new rProtos::Message());
	if (!m->ParseFromArray(data->data(), data->size()))
	{
		std::cout << "Failed to parse a packet!" << std::endl;
		return;
	}
	if (m->has_ping())
		executeMessage(RPROCESSOR_KEY_PING, m);
	if (m->has_pong())
		executeMessage(RPROCESSOR_KEY_PONG, m);
	if (m->has_inforequest())
		executeMessage(RPROCESSOR_KEY_INFOREQUEST, m);
	if (m->has_infoset())
		executeMessage(RPROCESSOR_KEY_INFOSET, m);
    if (m->has_command())
        executeMessage(RPROCESSOR_KEY_COMMAND, m);

}

bool rProcessor::executeMessage(std::string name, rMessage data)
{
	if (mFunctionMap.count(name) > 0)
	{
		mFunctionMap[name](data);
		return true;
	}
	return false;
}

void rProcessor::setMessageFunction(std::string name, boost::function<void(rMessage)> function)
{
	mFunctionMap[name] = function;
}

rMessage rProcessor::createMessage(unsigned int id)
{
	auto ret = rMessage(new rProtos::Message());
	ret->set_id(id);
	return ret;
}

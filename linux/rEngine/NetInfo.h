#pragma once

#include <string>

namespace netinfo
{
	void getIfInfo(std::string &, std::string &);
	void waitForIP(std::string &, std::string &);
};

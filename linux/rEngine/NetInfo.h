#pragma once

#include <string>

namespace netinfo
{
    void useLocal(bool);
    bool isLocalHost();
	void getIfInfo(std::string &, std::string &);
	void waitForIP(std::string &, std::string &);
};

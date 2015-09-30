#include "NetInfo.h"
#ifdef __linux__
#include <sys/types.h> 
#include <sys/socket.h> 
#include <netinet/in.h> 
#include <arpa/inet.h> 
#include <net/if.h> 
#include <netpacket/packet.h> 
#include <ifaddrs.h> 
#include <fstream>
#include "boost/regex.hpp"
#include "boost/algorithm/string.hpp"

namespace netinfo
{
	void getIfInfo(std::string &macAddr, std::string &ip_address)
	{
		std::string data;
		ip_address = "";
		std::string if_name = "";
		macAddr = "";
		std::ifstream macFile;
		FILE *fp = popen("ip addr show scope global", "r");
		if (fp)
		{
			char *p=NULL; 
			size_t n;
			while ((getline(&p, &n, fp) > 0) && p)
			{
				if (p = strstr(p, "inet ")) 
				{
					data = std::string(p);
					boost::regex e("inet (.*?)/.*global (.*?)\n");
					boost::smatch results;
					std::string::const_iterator start = data.begin();
					std::string::const_iterator end = data.end();
					while (boost::regex_search(start, end, results, e))
					{
						if (results.size() >= 3)
						{
							ip_address = results[1].str();
							if_name = results[2].str();
							std::stringstream ss;
							ss << "/sys/class/net/" << if_name << "/address";
							macFile.open(ss.str());
							macAddr = std::string(std::istreambuf_iterator<char>(macFile), 
				    								(std::istreambuf_iterator<char>()));
							macFile.close();
							boost::algorithm::trim_right(macAddr);
							if (if_name == "eth0")
								break;
						}
						start = results[0].second;
					}
				}
			}	
		}
		pclose(fp);
	}
};
#else

#include <winsock2.h> 
#include <iphlpapi.h> 
#include <vector>
#include <sstream>

namespace netinfo
{
	std::string getMAC()
	{
		std::string ip, mac;
		std::vector<unsigned char> buf; 
		DWORD bufLen = 0; 
		GetAdaptersAddresses(0, 0, 0, 0, &bufLen); 
		if(bufLen) 
		{ 
			 buf.resize(bufLen, 0); 
			 IP_ADAPTER_ADDRESSES * ptr = 
							 reinterpret_cast<IP_ADAPTER_ADDRESSES*>(&buf[0]); 
			 DWORD err = GetAdaptersAddresses(0, 0, 0, ptr, &bufLen); 
			 if(err == NO_ERROR) 
			 { 
				  while(ptr) 
				 { 
					  if(ptr->PhysicalAddressLength) 
					  {
						  if (ptr->FirstUnicastAddress == 0)
						  {
							  ptr = ptr->Next;
							  continue;
						  }
						  struct sockaddr_in *s = (struct sockaddr_in*)ptr->FirstUnicastAddress->Address.lpSockaddr;
						  if (s->sin_family != AF_INET)
						  {
							  ptr = ptr->Next;
							  continue;
						  }
						  char *t_ip = inet_ntoa(s->sin_addr);
						  ip = t_ip;

						  if (ptr->PhysicalAddressLength != 6)
						  {
							  ptr = ptr->Next;
							  continue;
						  }
						  std::ostringstream ss;
						  for (int i=0; i<6; ++i) 
						  {
							  if (i != 0) ss << ':';
							  ss.width(2); //< Use two chars for each byte
							  ss.fill('0'); //< Fill up with '0' if the number is only one hexadecimal digit
							  ss << std::hex << (int)(ptr->PhysicalAddress[i]);
						  }
						  mac = ss.str();
					  } 
					  ptr = ptr->Next; 
				  } 
			 } 
		}
		if (mac.empty())
			return "aa:bb:cc:dd:ee:ff";
		return mac;
	}
};
#endif

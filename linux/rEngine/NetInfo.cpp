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
#include <linux/netlink.h>
#include <linux/rtnetlink.h>
#include "boost/regex.hpp"
#include "boost/algorithm/string.hpp"

namespace netinfo
{
    bool localHost = false;
    void useLocal(bool val)
    {
        localHost = true;
    }

    bool isLocalHost()
    {
        return localHost;
    }

	void getIfInfo(std::string &macAddr, std::string &ip_address)
	{
		std::string data;
		ip_address = "";
		std::string if_name = "";
		macAddr = "";
		std::ifstream macFile;
		if (localHost)
		{
            macAddr = "aa:bb:cc:dd:ee:ff";
            ip_address = "127.0.0.1";
            return;
		}

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
					boost::regex e("inet (.*?)/.*global.*?(\\w+)\n");
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

    void waitForIP(std::string &macAddr, std::string &ip_address)
    {
     struct sockaddr_nl addr;
     int nls,len,rtl;
     char buffer[4096];
     struct nlmsghdr *nlh;
     struct ifaddrmsg *ifa;
     struct rtattr *rth;

     if ((nls = socket(PF_NETLINK, SOCK_RAW, NETLINK_ROUTE)) == -1)   perror ("socket failure\n");

     memset (&addr,0,sizeof(addr));
     addr.nl_family = AF_NETLINK;
     addr.nl_groups = RTMGRP_IPV4_IFADDR;

     if (bind(nls, (struct sockaddr *)&addr, sizeof(addr)) == -1)    perror ("bind failure\n");

     nlh = (struct nlmsghdr *)buffer;
     while ((len = recv (nls,nlh,4096,0)) > 0)
     {
         for (;(NLMSG_OK (nlh, len)) && (nlh->nlmsg_type != NLMSG_DONE); nlh = NLMSG_NEXT(nlh, len))
         {
             if (nlh->nlmsg_type != RTM_NEWADDR) continue; /* some other kind of announcement */

             ifa = (struct ifaddrmsg *) NLMSG_DATA (nlh);

             rth = IFA_RTA (ifa);
             rtl = IFA_PAYLOAD (nlh);
             for (;rtl && RTA_OK (rth, rtl); rth = RTA_NEXT (rth,rtl))
             {
                 char name[IFNAMSIZ];
                 uint32_t ipaddr;

                 if (rth->rta_type != IFA_LOCAL) continue;

                 ipaddr = * ((uint32_t *)RTA_DATA(rth));
                 getIfInfo(macAddr, ip_address);
                 return;
             }
         }
     }

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

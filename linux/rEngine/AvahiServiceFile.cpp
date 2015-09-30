#include "AvahiServiceFile.h"
#include "boost/regex.hpp"
#include <iostream>
#include <sstream>
#include <fstream>

namespace avahi {
	void readServiceInfo(std::string &data, rProtos::DeviceInfo &record)
	{
		boost::regex e("<txt-record>(.*?)=(.*?)</txt-record>");
		boost::smatch results;
		std::string::const_iterator start = data.begin();
		std::string::const_iterator end = data.end();
		while (boost::regex_search(start, end, results, e))
		{
			if (results.size() >= 3)
			{
				if (results[1].str() == "mode")
					record.set_mode(results[2].str());
				if (results[1].str() == "name")
					record.set_name(results[2].str());
				if (results[1].str() == "location")
					record.set_location(results[2].str());
				if (results[1].str() == "mac")
					record.set_mac(results[2].str());
			}
			start = results[0].second;
		}
	}

	bool readServiceInfoFile(const std::string &fname, rProtos::DeviceInfo &record)
	{
		std::ifstream f;
		f.open(fname);
		if (!f.is_open())
			return false;
		std::string data(std::istreambuf_iterator<char>(f), 
						 (std::istreambuf_iterator<char>()));
		readServiceInfo(data, record);
		f.close();
		return true;
	}


	void updateServiceInfo(std::string &data, const rProtos::InfoSet &record)
	{
		std::stringstream ss;
		boost::regex e;
		boost::regex s("</service>");
		bool res;
		if (record.has_name())
		{
			e = boost::regex("<txt-record>name=.*?</txt-record>");
			res = boost::regex_search(data, e);
			if (res)
			{
				ss << "<txt-record>name="<<record.name()<<"</txt-record>";
				data = boost::regex_replace(data, e, ss.str());
			}
			else
			{
				ss << "  <txt-record>name="<<record.name()<<"</txt-record>\n </service>";
				data = boost::regex_replace(data, s, ss.str());
			}
		}
		ss.str(std::string());
		if (record.has_location())
		{
			e = boost::regex("<txt-record>location=.*?</txt-record>");
			res = boost::regex_search(data, e);
			if (res)
			{
				ss << "<txt-record>location="<<record.location()<<"</txt-record>";
				data = boost::regex_replace(data, e, ss.str());
			}
			else
			{
				ss << "  <txt-record>location="<<record.location()<<"</txt-record>\n </service>";
				data = boost::regex_replace(data, s, ss.str());
			}
		}
	}

	void updateServiceInfoFile(const std::string &fname, const rProtos::InfoSet &record)
	{
		std::ifstream f;
		f.open(fname);
		std::string data(std::istreambuf_iterator<char>(f), 
						 (std::istreambuf_iterator<char>()));
		updateServiceInfo(data, record);
		f.close();
		std::ofstream o;
		o.open(fname, std::ofstream::out | std::ofstream::trunc);
		o << data;
		o.close();
	}

	void createServiceInfo(std::string &data, const rProtos::DeviceInfo &record)
	{
		std::stringstream ss;
		boost::regex s("</service>"), e;
		bool res;
		data = getData();
		if (record.has_name())
		{
			e = boost::regex("<txt-record>name=.*?</txt-record>");
			res = boost::regex_search(data, e);
			if (res)
			{
				ss << "<txt-record>name="<<record.name()<<"</txt-record>";
				data = boost::regex_replace(data, e, ss.str());
			}
			else
			{
				ss << "  <txt-record>name="<<record.name()<<"</txt-record>\n </service>";
				data = boost::regex_replace(data, s, ss.str());
			}
		}
		ss.str(std::string());
		if (record.has_location())
		{
			e = boost::regex("<txt-record>location=.*?</txt-record>");
			res = boost::regex_search(data, e);
			if (res)
			{
				ss << "<txt-record>location="<<record.location()<<"</txt-record>";
				data = boost::regex_replace(data, e, ss.str());
			}
			else
			{
				ss << "  <txt-record>location="<<record.location()<<"</txt-record>\n </service>";
				data = boost::regex_replace(data, s, ss.str());
			}
		}
		ss.str(std::string());
		if (record.has_mode())
		{
			e = boost::regex("<txt-record>mode=.*?</txt-record>");
			res = boost::regex_search(data, e);
			if (res)
			{
				ss << "<txt-record>mode="<<record.mode()<<"</txt-record>";
				data = boost::regex_replace(data, e, ss.str());
			}
			else
			{
				ss << "  <txt-record>mode="<<record.mode()<<"</txt-record>\n </service>";
				data = boost::regex_replace(data, s, ss.str());
			}
		}
		ss.str(std::string());
		if (record.has_mac())
		{
			e = boost::regex("<txt-record>mac=.*?</txt-record>");
			res = boost::regex_search(data, e);
			if (res)
			{
				ss << "<txt-record>mac="<<record.mac()<<"</txt-record>";
				data = boost::regex_replace(data, e, ss.str());
			}
			else
			{
				ss << "  <txt-record>mac="<<record.mac()<<"</txt-record>\n </service>";
				data = boost::regex_replace(data, s, ss.str());
			}
		}
		ss.str(std::string());
	}

	void createServiceInfoFile(const std::string &fname, const rProtos::DeviceInfo &record)
	{
		std::string data;
		createServiceInfo(data, record);
		std::ofstream o; 
		o.open(fname, std::ofstream::out | std::ofstream::trunc);
		o << data;
		o.close();
	}

	std::string getData()
	{
		std::string data = "<?xml version=\"1.0\" standalone='no'?>\n"
			"<!DOCTYPE service-group SYSTEM \"avahi-service.dtd\">\n"
			"<service-group>\n"
			" <name replace-wildcards=\"yes\">RemoteControl on %h</name>\n"
			" <service>\n"
			"   <type>_workstation._tcp</type>\n"
			"   <port>30015</port>\n"
			" </service>\n"
			"</service-group>\n";
		return data;
	}

	std::string getFakeData()
	{
		std::string data = 	"<?xml version=\"1.0\" standalone='no'?>\n"
			"<!DOCTYPE service-group SYSTEM \"avahi-service.dtd\">\n"
			"<service-group>\n"
			" <name replace-wildcards=\"yes\">RemoteControl on %h</name>\n"
			" <service>\n"
			"   <type>_workstation._tcp</type>\n"
			"   <port>30015</port>\n"
			"   <txt-record>name=Chomebox 1</txt-record>\n"
			"   <txt-record>mac=ab:cd:ef:00:11:22</txt-record>\n"
			"   <txt-record>mode=XBMC</txt-record>\n"
			" </service>\n"
			"</service-group>\n";
		return data;
	}
};

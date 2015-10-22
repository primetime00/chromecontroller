#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <signal.h>
#include <list>

#include <exception>
#include <array>
#include <vector>
#include <boost/bind.hpp>
#include "rEngine/rEngine.h"

#include "rNetwork/NetworkService.h"
#include "rNetwork/rServer.h"
#include "rNetwork/rTestClient.h"

#include "rProcessor/rProcessor.h"
#include "rProcessor/Keys.h"

#include <boost/make_shared.hpp>

#include <boost/log/trivial.hpp>
#include <boost/log/core.hpp>
#include <boost/log/expressions.hpp>

int serverRecvFunction(rConnection* connection, boost::shared_ptr<std::vector<uint8_t>> data)
{
	std::string val((*data).begin(), (*data).end());
	if (val == "hello ryan, how are you doing today?  Good, well that is great!")
	{
		BOOST_LOG_TRIVIAL(debug) << "got ping, will send response" ;
		char data[4];
		((unsigned int*)data)[0] = 4;
		std::string res = std::string(data, data+4)+std::string("PONG");
		connection->Send(std::vector<uint8_t>(res.begin(), res.end()));
		return 1;
	}
	return 0;
}

void client(boost::shared_ptr<NetworkService> ns) {
	boost::shared_ptr<rTestClient> client(new rTestClient(ns));
	client->Connect("127.0.0.1", 30015);
	client->SetTimeoutSeconds(15);
}

void server(boost::shared_ptr<NetworkService> ns) {
	boost::shared_ptr<rServer> server(new rServer(ns));
	server->Listen("127.0.0.1", 30015);
	server->SetRecvFunction(boost::bind(serverRecvFunction, _1, _2));
	server->AcceptConnection();
}

rEngine *engine = 0;

// Define the function to be called when ctrl-c (SIGINT) signal is sent to process
void signal_callback_handler(int signum)
{
   printf("Caught signal %d\n",signum);
   if (engine != 0 && (signum == SIGHUP || signum == SIGINT)) {
       engine->exit();
   }
   // Terminate program
//   exit(signum);
}

int main(int argc, char* argv[])
{
    bool server = true;
    boost::log::core::get()->set_filter(boost::log::trivial::severity >= boost::log::trivial::info);

    std::vector<char*> argList;
    argList.assign(argv, argv+argc);

    if (std::find(argList.begin(), argList.end(), std::string("-d")) != argList.end())
        boost::log::core::get()->set_filter(boost::log::trivial::severity >= boost::log::trivial::debug);
    if (std::find(argList.begin(), argList.end(), std::string("-c")) != argList.end())
        server=false;

    engine = new rEngine(server);

    if (server)
    {
        signal(SIGHUP, signal_callback_handler);
        signal(SIGINT, signal_callback_handler);
    }
	engine->run();
}








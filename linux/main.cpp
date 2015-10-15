#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>
#include <signal.h>

#include <iostream>
#include <exception>
#include <array>
#include <boost/bind.hpp>
#include "rEngine/rEngine.h"

#include "rNetwork/NetworkService.h"
#include "rNetwork/rServer.h"
#include "rNetwork/rTestClient.h"

#include "rProcessor/rProcessor.h"
#include "rProcessor/Keys.h"

#include <boost/make_shared.hpp>

int serverRecvFunction(rConnection* connection, boost::shared_ptr<std::vector<uint8_t>> data)
{
	std::string val((*data).begin(), (*data).end());
	if (val == "hello ryan, how are you doing today?  Good, well that is great!")
	{
		std::cout << "got ping, will send response" << std::endl;
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
   if (engine != 0 && signum == SIGHUP) {
       engine->exit();
   }
   // Terminate program
//   exit(signum);
}

int main(int argc, char* argv[])
{
	if (argc > 1 && std::string(argv[1]) == "client")
		engine = new rEngine(false);
	else
		engine = new rEngine();
    signal(SIGHUP, signal_callback_handler);
	engine->run();
}








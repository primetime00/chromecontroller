#pragma once
#include <boost/shared_ptr.hpp>
#include "ScriptCommand.pb.h"

#define APP_NAME "RemoteServer"

class rScripts
{
public:
    rScripts();
    ~rScripts();

    int numberOfScripts();
    rProtos::ScriptInfo *getScriptInfo(std::string name);
    rProtos::ScriptInfo *createScriptInfo(const std::string &name, const std::vector<std::string> &paramsList);
    rProtos::ScriptInfo *createScriptInfo(const std::string &name);
    rProtos::ScriptInfoList *getScriptInfoList();


    bool runScript(rProtos::ScriptInfo &);

private:
    void readScripts();
    bool writeScript(const rProtos::ScriptInfo &);

    boost::shared_ptr<rProtos::ScriptCommandList> scripts;

};

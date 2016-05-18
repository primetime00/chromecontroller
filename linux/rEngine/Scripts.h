#pragma once
#include <boost/shared_ptr.hpp>
#include "ScriptCommand.pb.h"
#include <boost/function.hpp>
#include <vector>

#define APP_NAME "ChromeController"

typedef boost::shared_ptr<rProtos::ScriptCommand> ScriptCommandPtr;
typedef boost::function<void(std::string, std::string)> ScriptHandler;

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

    void addStartupScriptHandler(ScriptHandler func);


    bool runScript(rProtos::ScriptInfo &);
    bool runScriptB(rProtos::ScriptInfo &);
    void readScripts();
    bool runScript(std::string, std::string &);

private:
    bool writeScript(const rProtos::ScriptInfo &);
    void postProcessScript(rProtos::ScriptCommand *);

    void processChoiceScripts(rProtos::ScriptInfo *info);
    void processStartScripts(rProtos::ScriptInfo *info);

    std::vector<ScriptHandler> mStartupScriptResults;

    boost::shared_ptr<rProtos::ScriptCommandList> scripts;

};

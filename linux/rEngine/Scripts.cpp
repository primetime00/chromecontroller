#include "Scripts.h"

#include <iostream>
#include "google/protobuf/text_format.h"
#include <fstream>
#include "boost/algorithm/string.hpp"
#include <boost/filesystem.hpp>
#include <sys/stat.h>
#include <stdio.h>
#include <sstream>

rScripts::rScripts() : scripts(new rProtos::ScriptCommandList())
{
    readScripts();
}

rScripts::~rScripts()
{
}

int rScripts::numberOfScripts()
{
    if (!scripts)
        return 0;
    return scripts->scripts_size();
}

void rScripts::readScripts()
{
    if (!boost::filesystem::exists("/etc/" APP_NAME "/scripts" ))
    {
        boost::filesystem::create_directories("/etc/" APP_NAME "/scripts");
        return;
    }
    boost::filesystem::directory_iterator end_iter;
    for (boost::filesystem::directory_iterator it("/etc/" APP_NAME "/scripts"); it != end_iter; ++it)
    {
        if (boost::filesystem::is_regular_file(it->status()))
        {
            if (it->path().extension().string().compare(".scp") == 0)
            {
                std::cout << "found file " << (it->path().string()) << std::endl;
                boost::shared_ptr<rProtos::ScriptCommand> cmd(new rProtos::ScriptCommand());
                std::ifstream v(it->path().string());
                if (v.is_open())
                {
                    std::string data(std::istreambuf_iterator<char>(v), (std::istreambuf_iterator<char>()));
                    google::protobuf::TextFormat::ParseFromString(data, cmd.get());
                    auto pos = data.find("#script");
                    if (pos == std::string::npos)
                    {
                        std::cout << "Error parsing script: " << it->path().string() << std::endl;
                        continue;
                    }
                    else
                    {
                        data = data.substr(pos+std::string("#script").size());
                        boost::algorithm::trim_left(data);
                        cmd->set_script(data);
                        auto script = scripts->add_scripts();
                        script->mutable_info()->CopyFrom(cmd->info());
                        script->set_script(cmd->script());
                    }
                }
            }
        }
    }
}

bool rScripts::writeScript(const rProtos::ScriptInfo &info)
{
    for (auto it : scripts->scripts())
    {
        if (it.info().name() == info.name()) //we have a match
        {
            std::ofstream f;
            f.open("/tmp/remote_script.sh");
            if (f.is_open())
            {
                f << it.script();
                f.close();
                chmod("/tmp/remote_script.sh", 775);
                return true;
            }
        }
    }
    return false;
}

bool rScripts::runScript(rProtos::ScriptInfo &info)
{
    std::string output="";
    bool res = writeScript(info);
    if (res == false) {
        std::cout << "Could not write the script file!";
        info.set_run_failed(true);
        return false;
    }
    std::stringstream ss;
    ss << "/tmp/remote_script.sh ";
    for (auto val : info.params())
    {
        ss << val << " ";
    }
    FILE *f = popen(ss.str().c_str(), "r");
    if (f == 0)
    {
        std::cout << "Could not execute remote script!";
        info.set_run_failed(true);
        return false;
    }
    char buffer[4000]; //4k buffer?
    while (fgets( buffer, 4000, f))
    {
        output += buffer;
    }
    auto exit_code = (pclose(f) / 256);
    info.set_return_data(output);
    info.set_return_value(exit_code);
    std::cout << "returns:\n" << output << std::endl;
    std::cout << "code:\n" << exit_code << std::endl;
    return true;
}

rProtos::ScriptInfo * rScripts::getScriptInfo(std::string name)
{
    auto start = scripts->mutable_scripts()->pointer_begin();
    auto end = scripts->mutable_scripts()->pointer_end();
    for (auto it = start; it != end; ++it)
    {
        if ( (*it)->info().name() == name) //we have a match
        {
            return (*it)->mutable_info();
        }
    }
    return 0;
}

rProtos::ScriptInfo *rScripts::createScriptInfo(const std::string &name, const std::vector<std::string> &paramList)
{
    rProtos::ScriptInfo *scriptInfo = getScriptInfo(name);
    if (scriptInfo == 0)
        return 0;
    std::string test = scriptInfo->name();
    rProtos::ScriptInfo *newScript = new rProtos::ScriptInfo();
    newScript->set_name(scriptInfo->name());
    newScript->set_output_type(scriptInfo->output_type());
    for (auto item : paramList)
    {
        newScript->add_params(item);
    }
    return newScript;
}

rProtos::ScriptInfo *rScripts::createScriptInfo(const std::string &name)
{
    return createScriptInfo(name, {});
}

rProtos::ScriptInfoList *rScripts::getScriptInfoList()
{
    rProtos::ScriptInfoList *scriptInfos = new rProtos::ScriptInfoList();
    for (auto it : scripts->scripts())
    {
        scriptInfos->add_scripts()->CopyFrom(it.info());
    }
    return scriptInfos;
}

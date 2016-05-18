#include "Scripts.h"

#include <boost/log/trivial.hpp>
#include "google/protobuf/text_format.h"
#include <fstream>
#include <chrono>
#include <thread>
#include "boost/algorithm/string.hpp"
#include "boost/date_time/posix_time/posix_time.hpp"
#include "boost/thread/thread.hpp"
#include "boost/process.hpp"
#include <boost/filesystem.hpp>
#include <sys/stat.h>
#include <stdio.h>
#include <sstream>

rScripts::rScripts() : scripts(new rProtos::ScriptCommandList())
{
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
                BOOST_LOG_TRIVIAL(debug) << "found file " << (it->path().string()) ;
                ScriptCommandPtr cmd(new rProtos::ScriptCommand());
                std::ifstream v(it->path().string());
                if (v.is_open())
                {
                    std::string data(std::istreambuf_iterator<char>(v), (std::istreambuf_iterator<char>()));
                    auto pos = data.find("#script");
                    if (pos == std::string::npos)
                    {
                        BOOST_LOG_TRIVIAL(debug) << "Error parsing script: " << it->path().string() ;
                        continue;
                    }
                    std::string protoData(data.begin(), data.begin()+pos);

                    google::protobuf::TextFormat::ParseFromString(protoData, cmd.get());

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
    auto start = scripts->mutable_scripts()->pointer_begin();
    auto end = scripts->mutable_scripts()->pointer_end();
    for (auto it = start; it != end; ++it)
        postProcessScript(*it);
}

void rScripts::postProcessScript(rProtos::ScriptCommand *cmd)
{
    rProtos::ScriptInfo *info = cmd->mutable_info();

    //check for startup scripts
    if (cmd->start_up())
    {
        processStartScripts(info);
    }
    //check for choice scripts
    if (info->has_choice())
    {
        processChoiceScripts(info);
    }
}

void rScripts::processChoiceScripts(rProtos::ScriptInfo *info)
{
    rProtos::ScriptChoice *choice = info->mutable_choice();
    if (choice->has_option_script())
    {
        std::string option_script = choice->option_script();
        rProtos::ScriptInfo cpInfo;
        cpInfo.CopyFrom(*getScriptInfo(option_script));
        runScript( cpInfo );
        if (!cpInfo.run_failed() && cpInfo.has_return_data())
        {
            std::string result = cpInfo.return_data();
            std::vector<std::string> option_list;
            boost::algorithm::trim(result);
            boost::split(option_list, result, boost::is_any_of("\n"), boost::token_compress_on);
            for (auto val : option_list)
                choice->add_option(val);
        }
    }
}

bool rScripts::runScript(std::string name, std::string &data)
{
    rProtos::ScriptInfo cpInfo;
    auto script = getScriptInfo(name);
    if (script == 0)
        return false;
    cpInfo.CopyFrom(*script);
    runScript( cpInfo );
    if (!cpInfo.run_failed() && cpInfo.has_return_data())
    {
        data = cpInfo.return_data();
        boost::algorithm::trim(data);
        return true;
    }
    return false;
}

void rScripts::processStartScripts(rProtos::ScriptInfo *info)
{
    rProtos::ScriptInfo cpInfo;
    auto script = getScriptInfo(info->name());
    if (script == 0)
        return;
    cpInfo.CopyFrom(*script);
    runScript( cpInfo );
    if (!cpInfo.run_failed() && cpInfo.has_return_data())
    {
        std::string result = cpInfo.return_data();
        boost::algorithm::trim(result);
        for (auto func : mStartupScriptResults)
        {
            func(cpInfo.name(), result);
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

bool rScripts::runScriptB(rProtos::ScriptInfo &info)
{
/*
    std::string output="";
    bool res = writeScript(info);
    if (res == false) {
        BOOST_LOG_TRIVIAL(debug) << "Could not write the script file!";
        info.set_run_failed(true);
        return false;
    }
    std::stringstream ss;
    ss << "/tmp/remote_script.sh ";
    for (auto val : info.params())
    {
        ss << val << " ";
    }
    if (info.output_type().compare("none") != 0) //we need to wait on the output
    {
        FILE *f = popen(ss.str().c_str(), "r");
        if (f == 0)
        {
            BOOST_LOG_TRIVIAL(debug) << "Could not execute remote script!";
            info.set_run_failed(true);
            return false;
        }
        char buffer[4000]; //4k buffer?
        while (fgets( buffer, 4000, f))
        {
            output += buffer;
        }
    }
    else //we don't care about the output
    {
        pid_t pid = fork();
        if (pid < 0)
        {
            BOOST_LOG_TRIVIAL(debug) << "Failed to fork";
            exit(0);
        }
        else if (pid == 0) //child
        {
        }
    }
    rProtos::ScriptInfo *original = getScriptInfo(info.name());
    info.Clear();
    info.MergeFrom(*original);
    auto val = pclose(f);
    auto exit_code = (val / 256);
    info.set_return_data(output);
    info.set_return_value(exit_code);
    if (exit_code == 127) //command was not found
        info.set_run_failed(true);
    BOOST_LOG_TRIVIAL(debug) << "returns:\n" << output ;
    BOOST_LOG_TRIVIAL(debug) << "code:\n" << exit_code ;*/
    return true;
}

bool rScripts::runScript(rProtos::ScriptInfo &info)
{

    std::string output="";
    bool res = writeScript(info);
    if (res == false) {
        BOOST_LOG_TRIVIAL(debug) << "Could not write the script file!";
        info.set_run_failed(true);
        return false;
    }
    std::string exec = "/tmp/remote_script.sh";
    std::vector<std::string> args;
    args.push_back(exec);
    for (auto val : info.params())
    {
        args.push_back(val);
    }
    boost::process::context ctx;
    if (info.output_type().compare("none") != 0) //we need to wait on the output
        ctx.stdout_behavior = boost::process::capture_stream();
    else
        ctx.stdout_behavior = boost::process::silence_stream();
    ctx.environment = boost::process::self::get_environment();
    {
        auto child = boost::process::launch(exec, args, ctx);

        if (info.output_type().compare("none") != 0) //we need to wait on the output
        {
            boost::process::pistream &is = child.get_stdout();
            std::string line;
            while (std::getline(is, line))
            {
                output+=line;
                output+="\n";
            }
            boost::process::posix_status s = child.wait();
            info.set_return_data(output);
            info.set_return_value(s.exit_status());
            if (s.exit_status() == 127) //command was not found
                info.set_run_failed(true);
            BOOST_LOG_TRIVIAL(debug) << "script:" << info.name() << " " << "returns: " << output ;
            BOOST_LOG_TRIVIAL(debug) << "code: " << s.exit_status() ;
            return true;
        }
        else
        {
            boost::process::posix_status s = child.wait();
            info.set_return_data("");
            info.set_return_value(0);
            BOOST_LOG_TRIVIAL(debug) << "launched " << boost::algorithm::join(args, " ");
            BOOST_LOG_TRIVIAL(debug) << "script:" << info.name() << " " << "returns: " << "(none)";
            BOOST_LOG_TRIVIAL(debug) << "code: " << 0 ;
            return true;
        }
    }
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

void rScripts::addStartupScriptHandler(ScriptHandler func)
{
    mStartupScriptResults.push_back(func);
}

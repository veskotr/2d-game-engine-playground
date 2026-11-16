#include <sle/engine/ProjectRuntime.hpp>
#include <sle/engine/Runtime.hpp>
#include <sle/engine/SceneAuthoring.hpp>
#include <sle/engine/EntityAuthoring.hpp>
#include <fstream>
#include <algorithm>
#include <cctype>
#include <functional>
#include <vector>
#include <set>
#include <regex>
#include <chrono>
#include <stdexcept>

namespace sle {
namespace {
namespace fs=std::filesystem;
using Json=nlohmann::json;
void require(bool condition,const std::string& message) { if(!condition) throw std::invalid_argument(message); }
bool uuid(const std::string& value) {
    static const std::regex pattern("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    return std::regex_match(value,pattern);
}
std::string identity(std::string value) {
    require(uuid(value),"Invalid project asset UUID: "+value);
    std::transform(value.begin(),value.end(),value.begin(),[](unsigned char c) { return static_cast<char>(std::tolower(c)); });return value;
}
Json read(const fs::path& path) {
    require(fs::is_regular_file(fs::symlink_status(path)) && fs::file_size(path)<=16*1024*1024,"Missing or oversized project file: "+path.generic_string());
    std::ifstream file(path);require(file.is_open(),"Cannot open project file: "+path.generic_string());Json result;file>>result;return result;
}
void keys(const Json& value,const std::set<std::string>& allowed) {
    require(value.is_object(),"Expected project JSON object");
    for(const auto& [key,child]:value.items()) require(allowed.contains(key),"Unknown project field: "+key);
}
int number(const Json& value) {
    require(value.is_number_integer() && value.get<double>()>=0 && value.get<double>()<=2147483647,"Expected nonnegative project integer");return value.get<int>();
}
void text(const Json& value,std::size_t max) {
    require(value.is_string(),"Expected project text");const auto string=value.get<std::string>();
    require(!string.empty() && string.size()<=max && std::any_of(string.begin(),string.end(),[](unsigned char c){return !std::isspace(c);}) &&
        std::none_of(string.begin(),string.end(),[](unsigned char c){return c<32 || c==127;}),"Invalid project text");
}
fs::path contained(const fs::path& root,const fs::path& candidate) {
    const auto normalized=fs::absolute(candidate).lexically_normal();
    auto relative=normalized.lexically_relative(root);
    require(!relative.empty() && !relative.is_absolute(),"Asset path is outside project");
    auto current=root;
    for(const auto& part:relative) {
        require(part!="..","Asset path escapes project");current/=part;
        require(!fs::is_symlink(fs::symlink_status(current)),"Project asset links are unsupported: "+current.generic_string());
    }
    return normalized;
}
bool suffix(const fs::path& path,const std::string& ending) {
    auto name=path.filename().string();std::transform(name.begin(),name.end(),name.begin(),[](unsigned char c){return static_cast<char>(std::tolower(c));});
    return name.ends_with(ending);
}
}
struct ProjectRuntime::Cache {
    fs::path path;
    ~Cache() { std::error_code ignored;fs::remove_all(path,ignored); }
};
core::Result<ProjectRuntime> ProjectRuntime::open(const fs::path& descriptor) {
    using Result=core::Result<ProjectRuntime>;
    try {
        ProjectRuntime project;
        const auto file=fs::canonical(descriptor);project.root=file.parent_path();
        const auto source=read(file);
        keys(source,{"format","version","id","name","assetRoot","startupScene","game"});
        require(source.at("format")=="sle-project" && source.at("version").is_number_integer() && source.at("version")==1,"Unsupported project format/version");
        identity(source.at("id").get<std::string>());text(source.at("name"),256);
        const auto relative=source.at("assetRoot").get<std::string>();
        require(!relative.empty() && relative.find_first_of("\\:")==std::string::npos && relative.front()!='/',"Invalid project asset root");
        for(const auto& part:fs::path(relative)) require(part!="." && part!=".." && !part.empty(),"Invalid project asset root");
        auto first=fs::path(relative).begin()->string();std::transform(first.begin(),first.end(),first.begin(),[](unsigned char c){return static_cast<char>(std::tolower(c));});
        require(first!=".sle" && relative.find("//")==std::string::npos && relative.back()!='/',"Invalid project asset root");
        project.assets=contained(project.root,project.root/relative);require(fs::is_directory(project.assets),"Missing project asset root");
        require(!source.at("startupScene").is_null(),"Choose a startup scene in Project Settings");
        project.startup=identity(source.at("startupScene").get<std::string>());
        const auto& game=source.at("game");keys(game,{"window","targetFPS"});const auto& window=game.at("window");
        keys(window,{"title","width","height","vsync","mode"});text(window.at("title"),512);
        auto& config=project.config;config.title=window.at("title").get<std::string>();config.width=number(window.at("width"));config.height=number(window.at("height"));
        require(config.width>=1 && config.width<=16384 && config.height>=1 && config.height<=16384,"Invalid project window dimensions");
        require(window.at("vsync").is_boolean(),"Invalid project vsync");config.vsync=window.at("vsync").get<bool>();
        const auto mode=window.at("mode").get<std::string>();require(mode=="Windowed" || mode=="Fullscreen" || mode=="BorderlessFullscreen","Invalid project window mode");
        config.screenMode=mode=="Fullscreen"?core::ScreenMode::Fullscreen:mode=="BorderlessFullscreen"?core::ScreenMode::BorderlessFullscreen:core::ScreenMode::Windowed;
        config.targetFPS=number(game.at("targetFPS"));
        std::vector<fs::path> documents;
        for(const auto& entry:fs::recursive_directory_iterator(project.assets)) {
            if(entry.is_symlink() || !entry.is_regular_file() || suffix(entry.path(),".slemeta")) continue;
            const auto sidecar=fs::path(entry.path().string()+".slemeta");
            // Runtime does not assign identities: unknown files may remain unindexed.
            if(!fs::exists(sidecar)) { require(!suffix(entry.path(),".scene.json") && !suffix(entry.path(),".prefab.json"),"Missing document asset metadata: "+entry.path().generic_string());continue; }
            const auto metadata=read(sidecar);keys(metadata,{"format","version","id"});
            require(metadata.at("format")=="sle-asset" && metadata.at("version").is_number_integer() && metadata.at("version")==1,"Invalid asset metadata");
            const auto id=identity(metadata.at("id").get<std::string>());
            require(project.paths.emplace(id,contained(project.assets,entry.path())).second,"Duplicate project asset UUID: "+id);
            if(suffix(entry.path(),".scene.json") || suffix(entry.path(),".prefab.json")) documents.push_back(entry.path());
        }
        std::map<fs::path,Json> prefabs;
        // Reserve derived prefab destinations before resolving cross-document fields.
        for(const auto& path:documents) if(suffix(path,".prefab.json")) {
            if(!project.cache) {
                const auto local=contained(project.root,project.root/".sle/runtime");fs::create_directories(local);
                auto owned=std::make_shared<Cache>();
                for(unsigned attempt=0;;++attempt) {
                    owned->path=local/(std::to_string(std::chrono::steady_clock::now().time_since_epoch().count())+"-"+std::to_string(attempt));
                    if(fs::create_directory(owned->path)) break;
                }
                project.cache=std::move(owned);
            }
            const auto id=identity(read(fs::path(path.string()+".slemeta")).at("id").get<std::string>());
            project.derived[id]=(project.cache->path/(id+".prefab.json")).generic_string();
        }
        std::set<std::string> aliases;
        for(const auto& path:documents) {
            const auto envelope=read(path);keys(envelope,{"format","version","document","assets"});
            const bool scene=suffix(path,".scene.json");
            require(envelope.at("format")== (scene?"sle-project-scene":"sle-project-prefab") && envelope.at("version").is_number_integer() && envelope.at("version")==1,"Invalid project document envelope");
            require(envelope.at("assets").is_array(),"Invalid project document assets");
            for(const auto& extra:envelope.at("assets")) project.resolveAsset("project-asset://"+extra.get<std::string>());
            auto document=envelope.at("document");
            for(auto& entity:document.at("entities")) entity["components"]=project.resolveComponentAssets(entity.at("components"),true);
            const auto id=identity(read(fs::path(path.string()+".slemeta")).at("id").get<std::string>());
            if(scene) {
                document=SceneAuthoring::canonicalScene(document);
                const auto key=document.at("key").get<std::string>();
                require(key==id || !project.paths.contains(key),"Scene key conflicts with a project UUID: "+key);
                require(aliases.insert(key).second,"Duplicate project scene key: "+key);
                project.scenes.emplace(id,Scene{key,path.generic_string(),std::move(document)});
            } else prefabs.emplace(path,EntityAuthoring::canonicalPrefab(document));
        }
        require(project.scenes.contains(project.startup),"Startup UUID does not identify a project scene");
        for(const auto& [path,document]:prefabs) {
            const auto id=identity(read(fs::path(path.string()+".slemeta")).at("id").get<std::string>());
            std::ofstream output(project.derived.at(id),std::ios::binary);output<<document.dump();output.close();require(!output.fail(),"Cannot write derived runtime prefab");
        }
        return Result::success(std::move(project));
    } catch(const std::exception& error) { return Result::error(std::string("Project launch: ")+error.what()); }
}
const nlohmann::json& ProjectRuntime::sceneDocument(const std::string& id) const { return scenes.at(identity(id)).document; }
const nlohmann::json* ProjectRuntime::findSceneDocument(const std::string& nameOrPath) const {
    const auto found=scenes.find(uuid(nameOrPath)?identity(nameOrPath):nameOrPath);
    if(found!=scenes.end()) return &found->second.document;
    for(const auto& [id,scene]:scenes) if(scene.key==nameOrPath || scene.path==nameOrPath) return &scene.document;
    return nullptr;
}
void ProjectRuntime::registerScenes(Runtime& runtime) const {
    for(const auto& [id,scene]:scenes) { runtime.registerSceneFromData(id,scene.document,scene.path);if(scene.key!=id) runtime.registerSceneFromData(scene.key,scene.document,scene.path); }
}
std::string ProjectRuntime::resolveAsset(const std::string& value) const {
    if(value.empty()) return value;
    constexpr std::string_view prefix="project-asset://";
    if(value.starts_with(prefix)) {
        const auto id=identity(value.substr(prefix.size()));const auto found=paths.find(id);
        require(found!=paths.end(),"Missing or ambiguous project asset UUID: "+id);
        require(fs::is_regular_file(contained(assets,found->second)),"Missing project asset: "+found->second.generic_string());
        const auto cached=derived.find(id);return cached!=derived.end()?cached->second:found->second.generic_string();
    }
    require(!value.starts_with("asset://"),"Unresolved package asset reference");
    auto path=fs::path(value);if(!path.is_absolute()) path=assets/path;
    path=contained(assets,path);require(fs::is_regular_file(path),"Missing project asset: "+path.generic_string());
    for(const auto& [id,original]:paths) if(path==original && derived.contains(id)) return derived.at(id);
    return path.generic_string();
}
nlohmann::json ProjectRuntime::resolveComponentAssets(const Json& components,bool portable) const {
    static const std::set<std::string> fields={"texture","scriptAsset","assetPath","layoutAsset","fontAsset","behaviorAsset","definitionAsset","clipAsset"};
    std::function<Json(const Json&,const std::string&)> visit=[&](const Json& value,const std::string& field)->Json {
        if(field=="variables" || field=="targetEntities") return value;
        if(value.is_object()) { auto result=value;for(auto& [key,child]:result.items()) child=visit(child,field=="stateClipMap"?"clipAsset":key);return result; }
        if(value.is_array()) { auto result=value;for(auto& child:result) child=visit(child,field);return result; }
        if(value.is_string() && fields.contains(field) && !value.get_ref<const std::string&>().empty()) {
            const auto path=value.get<std::string>();require(!portable || path.starts_with("project-asset://"),"Project source asset must use a UUID reference: "+path);return resolveAsset(path);
        }
        return value;
    };
    return visit(components,"");
}
}

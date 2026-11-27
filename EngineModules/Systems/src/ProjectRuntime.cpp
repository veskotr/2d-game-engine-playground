#include <sle/engine/ProjectRuntime.hpp>
#include <sle/engine/Runtime.hpp>
#include <sle/engine/SceneAuthoring.hpp>
#include <sle/engine/EntityAuthoring.hpp>
#include "GamePackage.hpp"
#include "ProjectSettings.hpp"
#include "PrefabExpansion.hpp"
#include "TileLayerLoader.hpp"
#include "TilemapLoader.hpp"
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
Json read(const fs::path& path, bool strict = false) {
    require(fs::is_regular_file(fs::symlink_status(path)) && fs::file_size(path)<=16*1024*1024,"Missing or oversized project file: "+path.generic_string());
    std::ifstream file(path);require(file.is_open(),"Cannot open project file: "+path.generic_string());
    if (strict) return Json::parse(file);
    Json result;file>>result;return result;
}
void keys(const Json& value,const std::set<std::string>& allowed) {
    require(value.is_object(),"Expected project JSON object");
    for(const auto& [key,child]:value.items()) require(allowed.contains(key),"Unknown project field: "+key);
}
struct AssetMetadata {
    std::string id;
    renderer::TextureFilter filter;
    std::map<std::string, ProjectRuntime::SpriteRegion> regions;
};
int number(const Json& value);
AssetMetadata readAssetMetadata(const fs::path& sidecar) {
    try {
        const auto metadata = read(sidecar, true);
        keys(metadata, {"format", "version", "id", "texture"});
        require(metadata.at("format") == "sle-asset" && metadata.at("version").is_number_integer() &&
            metadata.at("version") == 1, "Unsupported asset metadata");
        const auto id = identity(metadata.at("id").get<std::string>());
        auto filter = renderer::TextureFilter::Default;
        std::map<std::string, ProjectRuntime::SpriteRegion> regions;
        if (metadata.contains("texture")) {
            const auto& texture = metadata.at("texture");
            keys(texture, {"filter", "regions"});
            require(!texture.empty(), "Expected texture.filter or texture.regions");
            if (texture.contains("filter")) {
                const auto& value = texture.at("filter");
                require(value == "nearest" || value == "linear", "Expected nearest or linear for texture.filter");
                filter = value == "nearest" ? renderer::TextureFilter::Nearest : renderer::TextureFilter::Linear;
            }
            if (texture.contains("regions")) {
                const auto& entries = texture.at("regions");
                require(entries.is_array() && entries.size() <= 4096, "Expected texture.regions array");
                for (const auto& entry : entries) {
                    keys(entry, {"id", "name", "x", "y", "width", "height"});
                    const auto regionId = entry.at("id").get<std::string>();
                    require(identity(regionId) == regionId, "Expected lowercase region UUID");
                    const auto name = entry.at("name").get<std::string>();
                    require(name.size() <= 256 && name.find_first_not_of(" \t\r\n") != std::string::npos,
                        "Expected a region name of at most 256 UTF-8 bytes");
                    const ProjectRuntime::SpriteRegion region{number(entry.at("x")), number(entry.at("y")),
                        number(entry.at("width")), number(entry.at("height"))};
                    require(region.width > 0 && region.height > 0, "Invalid region rectangle");
                    require(regions.emplace(regionId, region).second, "Duplicate region ID");
                }
            }
        }
        return {id, filter, std::move(regions)};
    } catch (const std::exception& failure) {
        throw std::invalid_argument("Invalid asset metadata: " + sidecar.generic_string() + ". " + failure.what());
    }
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
// Resolves within whichever root contains the path; roots never overlap.
fs::path inRoots(const std::vector<fs::path>& roots,const fs::path& candidate) {
    const auto normalized=fs::absolute(candidate).lexically_normal();
    for(const auto& root:roots) {
        const auto relative=normalized.lexically_relative(root);
        if(!relative.empty() && !relative.is_absolute() && *relative.begin()!="..") return contained(root,normalized);
    }
    throw std::invalid_argument("Asset path is outside project");
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
core::Result<ProjectRuntime> ProjectRuntime::open(const fs::path& descriptor, bool validateTileAssets) {
    using Result=core::Result<ProjectRuntime>;
    try {
        ProjectRuntime project;
        const auto file=fs::canonical(descriptor);project.root=file.parent_path();
        const auto source=read(file);
        keys(source,{"format","version","id","name","assetRoot","startupScene","game","settings"});
        require(source.at("format")=="sle-project" && source.at("version").is_number_integer() && source.at("version")==1,"Unsupported project format/version");
        identity(source.at("id").get<std::string>());text(source.at("name"),256);
        const auto relative=source.at("assetRoot").get<std::string>();
        require(!relative.empty() && relative.find_first_of("\\:")==std::string::npos && relative.front()!='/',"Invalid project asset root");
        for(const auto& part:fs::path(relative)) require(part!="." && part!=".." && !part.empty(),"Invalid project asset root");
        auto first=fs::path(relative).begin()->string();std::transform(first.begin(),first.end(),first.begin(),[](unsigned char c){return static_cast<char>(std::tolower(c));});
        require(first!=".sle" && relative.find("//")==std::string::npos && relative.back()!='/',"Invalid project asset root");
        const auto assets=contained(project.root,project.root/relative);require(fs::is_directory(assets),"Missing project asset root");
        project.roots={assets};
        if (source.contains("settings")) {
            const auto& settings = source.at("settings");
            project.textureDefaults.emplace(assets, detail::artStyleFilter(settings));
            project.blockedPairs = detail::blockedCollisionPairs(settings);
            project.tags = detail::projectTags(settings);
            project.actions = detail::projectInputActions(settings);
        }
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
        project.runtimeFolder=contained(project.root,project.root/".sle/runtime");
        project.index(validateTileAssets);
        return Result::success(std::move(project));
    } catch(const std::exception& error) { return Result::error(std::string("Project launch: ")+error.what()); }
}
core::Result<ProjectRuntime> ProjectRuntime::openPackages(const std::vector<fs::path>& packages,const core::EngineConfig& engine) {
    using Result=core::Result<ProjectRuntime>;
    try {
        require(!packages.empty(),"Game configuration lists no packages");
        ProjectRuntime project;project.config=engine;
        for(const auto& package:packages) {
            auto mounted=gamepackage::mount(package);
            if (project.startup.empty() && mounted.mainScene) {
                project.startup = identity(*mounted.mainScene);
                project.blockedPairs = mounted.blockedPairs;
                project.tags = std::move(mounted.tags);
                project.actions = std::move(mounted.actions);
            }
            project.roots.push_back(fs::canonical(mounted.root));
            project.textureDefaults.emplace(project.roots.back(), mounted.textureDefault);
            if(mounted.owner) project.mounts.push_back(std::move(mounted.owner));
        }
        require(!project.startup.empty(),"No game package declares a main scene");
        // Unpacked packages are shared and immutable; derivatives stay per launch.
        project.runtimeFolder=fs::temp_directory_path()/"sle-package-runtime";
        project.index(true);
        return Result::success(std::move(project));
    } catch(const std::exception& error) { return Result::error(std::string("Game packages: ")+error.what()); }
}
void ProjectRuntime::index(bool validateTileAssets) {
    auto& project=*this;
    std::vector<fs::path> documents;
    for(const auto& assets:project.roots) for(const auto& entry:fs::recursive_directory_iterator(assets)) {
        if(entry.is_symlink() || !entry.is_regular_file() || suffix(entry.path(),".slemeta")) continue;
        auto sidecar = entry.path();
        sidecar += ".slemeta";
        // Runtime does not assign identities: unknown files may remain unindexed.
        if(!fs::exists(fs::symlink_status(sidecar))) { require(!suffix(entry.path(),".scene.json") && !suffix(entry.path(),".prefab.json"),"Missing document asset metadata: "+entry.path().generic_string());continue; }
        const auto metadata = readAssetMetadata(sidecar);
        const auto path = contained(assets, entry.path());
        require(project.paths.emplace(metadata.id, path).second, "Duplicate project asset UUID: " + metadata.id);
        if (metadata.filter != renderer::TextureFilter::Default) project.textureFilters.emplace(path, metadata.filter);
        if(suffix(entry.path(),".scene.json") || suffix(entry.path(),".prefab.json")) documents.push_back(entry.path());
    }
    std::map<fs::path,Json> prefabs;
    std::map<fs::path,Json> pendingScenes;
    // Reserve derived prefab destinations before resolving cross-document fields.
    for(const auto& path:documents) if(suffix(path,".prefab.json")) {
        if(!project.cache) {
            const auto& local=project.runtimeFolder;
            require(!fs::is_symlink(fs::symlink_status(local)),"Unsafe runtime prefab folder");fs::create_directories(local);
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
        if(scene) pendingScenes.emplace(path,std::move(document));
        else {
            keys(document,{"root","entities"});
            const auto root=document.at("root").get<std::string>();
            require(!root.empty() && root!="@scene","Invalid prefab root");
            auto canonical=SceneAuthoring::canonicalScene({{"entities",document.at("entities")}});
            std::map<std::string,std::string> parents;
            std::set<std::string> entityKeys;
            for(const auto& entity:canonical["entities"]) {
                const auto key=entity.at("key").get<std::string>();entityKeys.insert(key);
                if(entity.contains("parent")) parents[key]=entity.at("parent").get<std::string>();
            }
            require(entityKeys.contains(root) && !parents.contains(root),"Prefab root must identify its single root entity");
            for(auto key:entityKeys) {
                while(parents.contains(key)) key=parents.at(key); // SceneAuthoring already rejects cycles.
                require(key==root,"All prefab entities must descend from root");
            }
            document["entities"]=std::move(canonical["entities"]);
            prefabs.emplace(path,std::move(document));
        }
    }
    for(const auto& [path,document]:prefabs) {
        const auto id=identity(read(fs::path(path.string()+".slemeta")).at("id").get<std::string>());
        std::ofstream output(project.derived.at(id),std::ios::binary);output<<document.dump();output.close();require(!output.fail(),"Cannot write derived runtime prefab");
    }
    // All derived prefab files must exist before scene expansion (directory order is arbitrary).
    for(auto& [path,document]:pendingScenes) {
        if(document.contains("prefabs")) {
            require(document["prefabs"].is_array(),"prefabs must be an array");
            for(auto& instance:document["prefabs"]) {
                keys(instance,{"file","key","parent","overrides"});
                const auto file=instance.at("file").get<std::string>();
                require(file.starts_with("project-asset://"),"Project prefab must use a UUID reference");
                instance["file"]=project.resolveAsset(file);
                if(instance.contains("overrides")) {
                    require(instance["overrides"].is_object(),"Invalid prefab overrides");
                    for(auto& patch:instance["overrides"]) patch=project.resolveComponentAssets(patch,true);
                }
            }
            const auto expanded=detail::expandScenePrefabs(document,path.generic_string());
            require(expanded.ok(),expanded.error());document=expanded.value();
        }
        document=SceneAuthoring::canonicalScene(document);
        if (validateTileAssets && document.contains("tileLayers")) {
            // Export validation opens the staged package without a graphics context.
            (void)detail::loadTileLayers(document.at("tileLayers"),
                [&project](const auto& ref) { return project.resolveAsset(ref); }, {}, false, false);
        }
        if (validateTileAssets) for (const auto& entity : document.at("entities")) {
            const auto& components = entity.at("components");
            if (components.contains("Tilemap")) (void)detail::loadTilemap(components["Tilemap"].at("asset").get<std::string>(),
                [&project](const auto& ref) { return project.resolveAsset(ref); }, {}, false, false);
        }
        const auto id=identity(read(fs::path(path.string()+".slemeta")).at("id").get<std::string>());
        const auto key=document.at("key").get<std::string>();
        require(key==id || !project.paths.contains(key),"Scene key conflicts with a project UUID: "+key);
        require(aliases.insert(key).second,"Duplicate project scene key: "+key);
        project.scenes.emplace(id,Scene{key,path.generic_string(),std::move(document)});
    }
    require(project.scenes.contains(project.startup),"Startup UUID does not identify a project scene");
}
const nlohmann::json& ProjectRuntime::sceneDocument(const std::string& id) const { return scenes.at(identity(id)).document; }
const nlohmann::json* ProjectRuntime::findSceneDocument(const std::string& nameOrPath) const {
    const auto found=scenes.find(uuid(nameOrPath)?identity(nameOrPath):nameOrPath);
    if(found!=scenes.end()) return &found->second.document;
    for(const auto& [id,scene]:scenes) if(scene.key==nameOrPath || scene.path==nameOrPath) return &scene.document;
    return nullptr;
}
renderer::TextureFilter ProjectRuntime::textureFilter(const fs::path& path) const {
    if (path.empty() || (textureFilters.empty() && textureDefaults.empty())) return renderer::TextureFilter::Default;
    const auto normalized = fs::absolute(path).lexically_normal();
    if (const auto found = textureFilters.find(normalized); found != textureFilters.end()) return found->second;
    // Project paths are indexed under the canonical root (including on Windows
    // when callers use a short path for the same directory).
    std::error_code error;
    const auto canonical = fs::canonical(normalized, error);
    if (error) return renderer::TextureFilter::Default;
    if (const auto found = textureFilters.find(canonical); found != textureFilters.end()) return found->second;
    for (const auto& [root, filter] : textureDefaults) {
        const auto relative = canonical.lexically_relative(root);
        if (!relative.empty() && !relative.is_absolute() && *relative.begin() != "..") return filter;
    }
    return renderer::TextureFilter::Default;
}
renderer::TextureFilter ProjectRuntime::previewTextureFilter(const fs::path& root, const fs::path& path, renderer::TextureFilter fallback) {
    if (root.empty() || path.empty()) return renderer::TextureFilter::Default;
    auto asset = fs::absolute(path).lexically_normal();
    const auto insideRoot = [&] {
        const auto relative = asset.lexically_relative(root);
        return !relative.empty() && !relative.is_absolute() && *relative.begin() != "..";
    };
    if (!insideRoot()) {
        std::error_code error;
        asset = fs::canonical(asset, error);
        if (error || !insideRoot()) return renderer::TextureFilter::Default;
    }
    auto sidecar = contained(root, asset);
    sidecar += ".slemeta";
    if (!fs::exists(fs::symlink_status(sidecar))) return fallback;
    const auto filter = readAssetMetadata(sidecar).filter;
    return filter == renderer::TextureFilter::Default ? fallback : filter;
}
std::map<std::string, ProjectRuntime::SpriteRegion> ProjectRuntime::readSpriteRegions(const fs::path& texture) {
    auto sidecar = texture;
    sidecar += ".slemeta";
    if (!fs::exists(fs::symlink_status(sidecar))) return {};
    return readAssetMetadata(sidecar).regions;
}
std::string ProjectRuntime::resolveEditorAsset(const fs::path& root, const std::string& reference) {
    constexpr std::string_view prefix = "project-asset://";
    require(!root.empty() && reference.starts_with(prefix), "No open project for " + reference);
    const auto requested = identity(reference.substr(prefix.size()));
    fs::path found;
    for (const auto& entry : fs::recursive_directory_iterator(root)) {
        if (entry.is_symlink() || !entry.is_regular_file() || !suffix(entry.path(), ".slemeta")) continue;
        // Unrelated invalid sidecars are reported by the asset browser.
        std::string candidate;
        try { candidate = readAssetMetadata(entry.path()).id; }
        catch (const std::exception&) { continue; }
        if (candidate != requested) continue;
        auto source = entry.path();
        source.replace_extension();
        if (!fs::is_regular_file(fs::symlink_status(source))) continue;
        require(found.empty(), "Ambiguous project asset UUID: " + requested);
        found = contained(root, source);
    }
    require(!found.empty(), "Missing project asset UUID: " + requested);
    return found.generic_string();
}
fs::path ProjectRuntime::resolveEditorSource(const fs::path& root, const fs::path& path) {
    require(!root.empty() && path.is_absolute(), "Reload requires a saved source in the open project");
    const auto source = contained(root, path);
    require(fs::is_regular_file(source), "Missing project source: " + source.generic_string());
    return source;
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
        require(fs::is_regular_file(inRoots(roots,found->second)),"Missing project asset: "+found->second.generic_string());
        const auto cached=derived.find(id);return cached!=derived.end()?cached->second:found->second.generic_string();
    }
    require(!value.starts_with("asset://"),"Unresolved package asset reference");
    auto path=fs::path(value);
    if(path.is_absolute()) path=inRoots(roots,path);
    else {
        // Earlier roots (packages listed first) win for relative dynamic paths.
        fs::path resolved;
        for(const auto& root:roots) if(const auto candidate=contained(root,root/path);fs::is_regular_file(candidate)) { resolved=candidate;break; }
        path=resolved.empty()?contained(roots.front(),roots.front()/path):resolved;
    }
    require(fs::is_regular_file(path),"Missing project asset: "+path.generic_string());
    for(const auto& [id,original]:paths) if(path==original && derived.contains(id)) return derived.at(id);
    return path.generic_string();
}
nlohmann::json ProjectRuntime::resolveComponentAssets(const Json& components,bool portable) const {
    static const std::set<std::string> fields={"texture","scriptAsset","assetPath","layoutAsset","fontAsset","behaviorAsset","definitionAsset","clipAsset"};
    std::function<Json(const Json&,const std::string&)> visit=[&](const Json& value,const std::string& field)->Json {
        if(field=="variables" || field == "targetEntities" || field == "parameters") return value;
        if(value.is_object()) { auto result=value;for(auto& [key,child]:result.items()) child=visit(child,field=="stateClipMap"?"clipAsset":key);return result; }
        if(value.is_array()) { auto result=value;for(auto& child:result) child=visit(child,field);return result; }
        if(value.is_string() && fields.contains(field) && !value.get_ref<const std::string&>().empty()) {
            const auto path=value.get<std::string>();require(!portable || path.starts_with("project-asset://"),"Project source asset must use a UUID reference: "+path);return resolveAsset(path);
        }
        return value;
    };
    auto result = visit(components, "");
    if (result.contains("Tilemap") && result["Tilemap"].contains("asset")) {
        const auto asset = result["Tilemap"]["asset"].get<std::string>();
        if (!asset.empty()) {
            require(!portable || asset.starts_with("project-asset://"), "Project tilemap must use a UUID reference");
            result["Tilemap"]["asset"] = resolveAsset(asset);
        }
    }
    return result;
}
}

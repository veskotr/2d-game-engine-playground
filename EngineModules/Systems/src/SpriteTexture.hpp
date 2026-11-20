#pragma once
#include <sle/renderer/Texture.hpp>
#include <sle/resources/Resources.hpp>
#include <filesystem>

namespace sle::detail {
struct SpriteTextureSource {
    std::filesystem::file_time_type modified;
    std::uintmax_t size;
};

inline std::shared_ptr<renderer::Texture> loadSpriteTexture(const std::string& path, renderer::TextureFilter filter,
    bool trackImageChanges = false)
{
    auto key = path;
    if (filter != renderer::TextureFilter::Default) {
        // A NUL separates the sampler from a real filesystem path.
        key += '\0';
        key += std::to_string(static_cast<int>(filter));
    }
    if (trackImageChanges) {
        std::error_code error;
        const auto modified = std::filesystem::last_write_time(path, error);
        if (!error) {
            const auto size = std::filesystem::file_size(path, error);
            if (!error) {
                const auto source = core::Resources::get<SpriteTextureSource>(key);
                if (!source || source->modified != modified || source->size != size) {
                    core::Resources::unload<renderer::Texture>(key);
                    auto updated = core::Resources::create<SpriteTextureSource>(key);
                    *updated = {modified, size};
                }
            }
        }
    }
    renderer::TextureLoadOptions options;
    options.minFilter = options.magFilter = filter;
    return core::Resources::create<renderer::Texture>(key, path, options);
}
}

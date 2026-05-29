#pragma once

#include <nlohmann/json.hpp>
#include <glm/glm.hpp>

#include <algorithm>
#include <cctype>
#include <cmath>
#include <fstream>
#include <memory>
#include <string>
#include <vector>

namespace sle::components {

enum class AnimationLoopMode
{
    Once,
    Loop,
};

enum class AnimationInterpolation
{
    Step,
    Linear,
    EaseIn,
    EaseOut,
    EaseInOut,
    Smoothstep,
    Exp,
    Log,
};

struct AnimationFloatKeyframe
{
    float timeSeconds = 0.0f;
    float value = 0.0f;
    AnimationInterpolation interpolation = AnimationInterpolation::Linear;
};

struct AnimationVec2Keyframe
{
    float timeSeconds = 0.0f;
    glm::vec2 value{0.0f};
    AnimationInterpolation interpolation = AnimationInterpolation::Linear;
};

struct AnimationVec4Keyframe
{
    float timeSeconds = 0.0f;
    glm::vec4 value{0.0f};
    AnimationInterpolation interpolation = AnimationInterpolation::Linear;
};

struct AnimationFloatTrack
{
    std::string bindingPath;
    std::vector<AnimationFloatKeyframe> keyframes;
};

struct AnimationVec2Track
{
    std::string bindingPath;
    std::vector<AnimationVec2Keyframe> keyframes;
};

struct AnimationVec4Track
{
    std::string bindingPath;
    std::vector<AnimationVec4Keyframe> keyframes;
};

struct AnimationClipDefinition
{
    std::string name;
    float lengthSeconds = 0.0f;
    AnimationLoopMode loopMode = AnimationLoopMode::Loop;
    std::vector<AnimationFloatTrack> floatTracks;
    std::vector<AnimationVec2Track> vec2Tracks;
    std::vector<AnimationVec4Track> vec4Tracks;
};

class AnimationClipResource
{
public:
    bool loadFromFiles(const std::string& definitionPath)
    {
        std::ifstream file(definitionPath);
        if (!file.is_open())
            return false;

        nlohmann::json json;
        try
        {
            file >> json;
        }
        catch (...)
        {
            return false;
        }

        auto parsed = std::make_shared<AnimationClipDefinition>();
        parsed->name = json.value("name", "");
        parsed->lengthSeconds = json.value("lengthSeconds", 0.0f);

        const std::string loopMode = toLower(json.value("loopMode", "loop"));
        parsed->loopMode = (loopMode == "once") ? AnimationLoopMode::Once : AnimationLoopMode::Loop;

        if (json.contains("tracks") && json["tracks"].is_array())
        {
            for (const auto& trackJson : json["tracks"])
            {
                if (!trackJson.is_object())
                    continue;

                const std::string valueType = toLower(trackJson.value("valueType", "float"));

                if (valueType == "vec2")
                {
                    AnimationVec2Track track;
                    track.bindingPath = trackJson.value("bindingPath", "");
                    if (track.bindingPath.empty() || !trackJson.contains("keys") || !trackJson["keys"].is_array())
                        continue;

                    for (const auto& keyJson : trackJson["keys"])
                    {
                        if (!keyJson.is_object())
                            continue;

                        AnimationVec2Keyframe key;
                        key.timeSeconds = keyJson.value("timeSeconds", 0.0f);
                        key.interpolation = parseInterpolation(keyJson.value("interp", "linear"));

                        if (keyJson.contains("value") && keyJson["value"].is_array() && keyJson["value"].size() >= 2)
                        {
                            key.value.x = keyJson["value"][0].get<float>();
                            key.value.y = keyJson["value"][1].get<float>();
                        }
                        track.keyframes.push_back(key);
                    }

                    if (track.keyframes.empty())
                        continue;

                    std::sort(
                        track.keyframes.begin(),
                        track.keyframes.end(),
                        [](const AnimationVec2Keyframe& a, const AnimationVec2Keyframe& b)
                        {
                            return a.timeSeconds < b.timeSeconds;
                        });

                    parsed->vec2Tracks.push_back(std::move(track));
                    continue;
                }

                if (valueType == "vec4")
                {
                    AnimationVec4Track track;
                    track.bindingPath = trackJson.value("bindingPath", "");
                    if (track.bindingPath.empty() || !trackJson.contains("keys") || !trackJson["keys"].is_array())
                        continue;

                    for (const auto& keyJson : trackJson["keys"])
                    {
                        if (!keyJson.is_object())
                            continue;

                        AnimationVec4Keyframe key;
                        key.timeSeconds = keyJson.value("timeSeconds", 0.0f);
                        key.interpolation = parseInterpolation(keyJson.value("interp", "linear"));

                        if (keyJson.contains("value") && keyJson["value"].is_array() && keyJson["value"].size() >= 4)
                        {
                            key.value.r = keyJson["value"][0].get<float>();
                            key.value.g = keyJson["value"][1].get<float>();
                            key.value.b = keyJson["value"][2].get<float>();
                            key.value.a = keyJson["value"][3].get<float>();
                        }
                        track.keyframes.push_back(key);
                    }

                    if (track.keyframes.empty())
                        continue;

                    std::sort(
                        track.keyframes.begin(),
                        track.keyframes.end(),
                        [](const AnimationVec4Keyframe& a, const AnimationVec4Keyframe& b)
                        {
                            return a.timeSeconds < b.timeSeconds;
                        });

                    parsed->vec4Tracks.push_back(std::move(track));
                    continue;
                }

                if (valueType != "float")
                    continue;

                AnimationFloatTrack track;
                track.bindingPath = trackJson.value("bindingPath", "");
                if (track.bindingPath.empty())
                    continue;

                if (!trackJson.contains("keys") || !trackJson["keys"].is_array())
                    continue;

                for (const auto& keyJson : trackJson["keys"])
                {
                    if (!keyJson.is_object())
                        continue;

                    AnimationFloatKeyframe key;
                    key.timeSeconds = keyJson.value("timeSeconds", 0.0f);
                    key.value = keyJson.value("value", 0.0f);
                    key.interpolation = parseInterpolation(keyJson.value("interp", "linear"));
                    track.keyframes.push_back(key);
                }

                if (track.keyframes.empty())
                    continue;

                std::sort(
                    track.keyframes.begin(),
                    track.keyframes.end(),
                    [](const AnimationFloatKeyframe& a, const AnimationFloatKeyframe& b)
                    {
                        return a.timeSeconds < b.timeSeconds;
                    });

                parsed->floatTracks.push_back(std::move(track));
            }
        }

        if (parsed->lengthSeconds <= 0.0f)
            return false;

        if (parsed->floatTracks.empty() && parsed->vec2Tracks.empty() && parsed->vec4Tracks.empty())
            return false;

        path_ = definitionPath;
        clip_ = std::move(parsed);
        return true;
    }

    const std::string& getPath() const { return path_; }
    const std::shared_ptr<AnimationClipDefinition>& getClip() const { return clip_; }

private:
    static std::string toLower(std::string s)
    {
        for (char& c : s)
            c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
        return s;
    }

    static AnimationInterpolation parseInterpolation(const std::string& raw)
    {
        const std::string value = toLower(raw);
        if (value == "step")
            return AnimationInterpolation::Step;
        if (value == "ease_in")
            return AnimationInterpolation::EaseIn;
        if (value == "ease_out")
            return AnimationInterpolation::EaseOut;
        if (value == "ease_in_out")
            return AnimationInterpolation::EaseInOut;
        if (value == "smoothstep")
            return AnimationInterpolation::Smoothstep;
        if (value == "exp")
            return AnimationInterpolation::Exp;
        if (value == "log")
            return AnimationInterpolation::Log;
        return AnimationInterpolation::Linear;
    }

    std::string path_;
    std::shared_ptr<AnimationClipDefinition> clip_;
};

} // namespace sle::components

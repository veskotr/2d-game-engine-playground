#pragma once
#include <sle/scene/Registry.hpp>
#include <nlohmann/json.hpp>
#include <glm/glm.hpp>
#include <algorithm>
#include <array>
#include <cmath>
#include <span>
#include <string_view>
#include <limits>
#include <stdexcept>

namespace sle::lua::components {
using Json = nlohmann::json;
inline constexpr float positiveMinimum = 0.0f;
inline constexpr float nonnegativeMinimum = 0.0f;
inline constexpr float unitMinimum = 0.0f;
inline constexpr float unitMaximum = 1.0f;
inline constexpr int signedIntegerMinimum = std::numeric_limits<int>::min();
inline constexpr int signedIntegerMaximum = std::numeric_limits<int>::max();
inline constexpr unsigned unsignedIntegerMinimum = 0;
inline constexpr unsigned bitMaskMaximum = 65535;

inline std::span<const std::string_view> enumChoices(std::string_view kind) {
 static constexpr std::array<std::string_view, 3> body{"Static", "Dynamic", "Kinematic"};
 static constexpr std::array<std::string_view, 2> loop{"Once", "Loop"};
 static constexpr std::array<std::string_view, 2> space{"Screen", "World"};
 static constexpr std::array<std::string_view, 4> mode{"Inherit", "AlwaysActive", "EventsOnly", "AutoToggle"};
 if (kind=="body") return body;
 if (kind=="loop") return loop;
 if (kind=="space") return space;
 if (kind=="mode") return mode;
 return {};
}

inline float number(const Json& j) {
 if (!j.is_number()) throw std::invalid_argument("Expected number");
 double v=j.get<double>();
 if (!std::isfinite(v) || std::abs(v)>std::numeric_limits<float>::max()) throw std::invalid_argument("Number must be finite and fit float");
 return static_cast<float>(v);
}
inline void validate(const Json& j, const std::string& kind) {
 if (kind=="bool") { if (!j.is_boolean()) throw std::invalid_argument("Expected boolean"); }
 else if (kind=="str") { if (!j.is_string()) throw std::invalid_argument("Expected string"); }
 else if (kind=="v2" || kind=="positivev2" || kind=="v4") {
  const int n=kind=="v4"?4:2; const char* keys[]={"x","y","z","w"};
  if (!j.is_object() || j.size()!=static_cast<size_t>(n)) throw std::invalid_argument("Expected complete vector object");
  for(int i=0;i<n;++i) { float v=number(j.at(keys[i])); if(kind=="positivev2" && v<=positiveMinimum) throw std::invalid_argument("Size must be positive"); }
 }
 else if(kind=="int" || kind=="u16" || kind=="u32") {
  if(!j.is_number_integer()) throw std::invalid_argument("Expected integer");
  const double v=j.get<double>(); const double low=kind=="int"?signedIntegerMinimum:static_cast<double>(unsignedIntegerMinimum);
  const double high=kind=="int"?signedIntegerMaximum:(kind=="u16"?bitMaskMaximum:4294967295.0);
  if(v<low || v>high) throw std::invalid_argument("Integer outside supported range");
 }
 else if(kind=="strmap" || kind=="idmap" || kind=="floatmap" || kind=="boolmap") {
  if(!j.is_object()) throw std::invalid_argument("Expected map");
  for(const auto& v:j) validate(v,kind=="strmap"?"str":kind=="idmap"?"u32":kind=="boolmap"?"bool":"float");
 }
 else if(kind=="strings") { if(!j.is_array() && !(j.is_object() && j.empty())) throw std::invalid_argument("Expected string array"); for(const auto& v:j) validate(v,"str"); }
 else if(const auto choices=enumChoices(kind); !choices.empty()) {
  validate(j,"str");
  const auto& value=j.get_ref<const Json::string_t&>();
  if(std::find(choices.begin(),choices.end(),value)==choices.end()) throw std::invalid_argument("Unknown enum value");
 }
 else { float v=number(j); if((kind=="nonneg" && v<nonnegativeMinimum)||(kind=="positive" && v<=positiveMinimum)||(kind=="unit" && (v<unitMinimum||v>unitMaximum))) throw std::invalid_argument("Number outside supported range"); }
}
inline Json vec(glm::vec2 v) { return {{"x",v.x},{"y",v.y}}; }
inline Json vec(glm::vec4 v) { return {{"x",v.x},{"y",v.y},{"z",v.z},{"w",v.w}}; }

template<class T> inline void invalidate(T&,const T*) {}
} // namespace sle::lua::components

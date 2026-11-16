#pragma once

#include <cstring>

// Only startup developer options traced to the retail cheat flags are allowed.
// No unlock/money options, collectible edits or instruction patches.
namespace mcla::cheats {
struct LaunchOption {
  const char* game_name;
  const char* environment;
};
inline constexpr LaunchOption kLaunchOptions[] = {
    {"unlimitednitrous", "MCLA_CHEAT_NITRO"},
    {"nodamage", "MCLA_CHEAT_NO_DAMAGE"},
    {"unlimitedspecial", "MCLA_CHEAT_SPECIAL"},
};

inline bool Enabled(const char* value) {
  return value && std::strcmp(value, "1") == 0;
}
}  // namespace mcla::cheats

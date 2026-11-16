#include "cheat_launch_policy.h"

#include <cassert>
#include <cstring>
#include <iostream>

int main() {
  using namespace mcla::cheats;
  assert(!Enabled(nullptr));
  assert(!Enabled(""));
  assert(!Enabled("0"));
  assert(!Enabled("false"));
  assert(!Enabled("true"));
  assert(!Enabled("01"));
  assert(!Enabled("1 "));
  assert(Enabled("1"));
  assert(sizeof(kLaunchOptions) / sizeof(kLaunchOptions[0]) == 3);
  assert(std::strcmp(kLaunchOptions[0].game_name, "unlimitednitrous") == 0);
  assert(std::strcmp(kLaunchOptions[1].game_name, "nodamage") == 0);
  assert(std::strcmp(kLaunchOptions[2].game_name, "unlimitedspecial") == 0);
  for (const auto& option : kLaunchOptions) {
    assert(std::strstr(option.game_name, "unlock") == nullptr);
    assert(std::strstr(option.game_name, "money") == nullptr);
  }
  std::cout << "PASS native cheat launch allowlist and strict OFF defaults\n";
}

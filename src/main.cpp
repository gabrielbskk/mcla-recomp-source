// mcla - ReXGlue Recompiled Project

#include "generated/default/mcla_init.h"

#include "mcla_app.h"

#ifdef __ANDROID__
extern "C" __attribute__((visibility("default"))) int SDL_main(int argc, char* argv[]);
#endif

REX_DEFINE_APP(mcla, MclaApp::Create)

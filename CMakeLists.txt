# mcla - ReXGlue Recompiled Project
#
# SDK boilerplate lives in generated/rexglue.cmake.

cmake_minimum_required(VERSION 3.25)
project(mcla LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(ANDROID AND NOT DEFINED CACHE{REXGLUE_RECOMP_DEBUG_INFO})
    # Keep release APKs lean and fast to load: src/crash_trace.cpp needs no DWARF line tables.
    set(REXGLUE_RECOMP_DEBUG_INFO "none" CACHE STRING
        "Debug info level for generated code: line-tables-only, full, or none")
endif()

include(generated/rexglue.cmake)

if(MINGW)
    # llvm-mingw (archive/fork-base/scripts/make_pc.ps1 -Toolchain mingw). The SDK is built the same
    # way (patches/rexglue-mingw.patch); here, not in the SDK's template of
    # generated/rexglue.cmake, so that every platform's codegen writes that file
    # as it is in the repository.
    # __try/__except needs -fms-extensions, and mingw-w64 assumes Windows 7
    # (_WIN32_WINNT 0x601) where the Windows SDK assumes the current Windows.
    add_compile_options($<$<COMPILE_LANGUAGE:CXX>:-fms-extensions>)
    add_compile_definitions(_WIN32_WINNT=0x0A00)
    # No -fasync-exceptions for the generated code: for a MinGW target the Clang
    # driver drops it ("argument unused", on every file), and passed on to the
    # compiler itself (-Xclang) it crashed Clang 23.1.3 in SimplifyCFG on most host
    # files. A fault inside a guest __try scope is therefore not caught with this
    # toolchain. Midnight Club: Los Angeles has no such scope (0 of 29,848
    # functions); a title that has needs the MSVC toolchain. The sources carry the
    # list as it was when the file was included, so it is set again.
    list(REMOVE_ITEM REXGLUE_RECOMP_OPTIONS -fasync-exceptions)
    if(REXGLUE_ENTRYPOINT_GENERATED_SOURCES)
        set_source_files_properties(${REXGLUE_ENTRYPOINT_GENERATED_SOURCES}
            PROPERTIES COMPILE_OPTIONS "${REXGLUE_RECOMP_OPTIONS}")
    endif()
endif()

# Sources
set(MCLA_SOURCES
    src/main.cpp
    src/mcla_app.cpp
    src/aspect_ratio.cpp
    src/crash_trace.cpp
    src/audio_fallback.cpp
    src/audio_mixer.cpp
    # The C library's fma replaced for a CPU without FMA3 (llvm-mingw, x86-64 only; the file is empty elsewhere).
    src/fma_fast.cpp
    src/frame_timing.cpp
    src/fps_counter.cpp
    src/render_perf.cpp
    src/perf_options.cpp
    src/poll_wait.cpp
    src/warp_fix.cpp
    src/button_prompts.cpp
    src/settings_menu.cpp
    src/pad_probe.cpp
    src/save_wait.cpp
    src/display_gamma.cpp
    src/render_markers.cpp
    src/vehicle_telemetry.cpp
    # Controller vibration on or off (mcla_vibration).
    src/vibration.cpp
    # A dead zone for each stick (mcla_left_stick_deadzone, mcla_right_stick_deadzone).
    src/stick_deadzone.cpp
    # DualSense adaptive triggers through SDL (fork only; the PS5 build has ps5/game/ps5_triggers.h).
    src/pc_triggers.cpp
    src/sampling_profiler.cpp
    src/code_pointer_scan.cpp
    # The shader pack's import and --mcla_prepare_shaders.
    src/shader_prepare.cpp
    src/shader_storage_files.cpp
    # Development: scripted controller input, off by default (fork only).
    src/dev_pad.cpp
)

if(ANDROID)
    list(APPEND MCLA_SOURCES src/touch_controls.cpp)
    # Reject obsolete build recipes instead of silently producing an Adreno APK.
    if(MCLA_MALI_DIAGNOSTIC_APK)
        message(FATAL_ERROR "Mali diagnostics have been retired; Android targets Snapdragon/Adreno")
    endif()
endif()

if(ANDROID)
    # SDL's Android activity loads libmain.so and calls SDL_main from it.
    # Keep the package arm64-only in the Android Gradle template.
    set(CMAKE_POSITION_INDEPENDENT_CODE ON)
    add_library(mcla SHARED ${MCLA_SOURCES})
    set_target_properties(mcla PROPERTIES OUTPUT_NAME main)
elseif(WIN32)
    add_executable(mcla WIN32 ${MCLA_SOURCES})
else()
    add_executable(mcla ${MCLA_SOURCES})
endif()

if(ANDROID)
    rexglue_setup_target(mcla GPU_PLUGINS xenos)

    # Resolve all 29,848 recompiled guest functions directly instead of routing
    # every inter-function branch through PLT/GOT indirection in libmain.so, and
    # enable ARMv8.2 LSE hardware atomics + Snapdragon scheduling in Release.
    set(_mcla_android_opt_flags
        -O3
        -march=armv8.2-a+lse+fp16+dotprod
        -mtune=cortex-a715
        -fno-math-errno
        -fdata-sections
        -ffunction-sections)
    target_compile_options(mcla PRIVATE
        $<$<CONFIG:Release,RelWithDebInfo>:${_mcla_android_opt_flags}>)
    if(TARGET mcla_recomp)
        set_target_properties(mcla_recomp PROPERTIES
            CXX_VISIBILITY_PRESET hidden
            C_VISIBILITY_PRESET hidden
            VISIBILITY_INLINES_HIDDEN ON)
        # Midnight Club: Los Angeles has 0 guest exception scopes; omitting unwind
        # tables shrinks libmain.so by tens of MB and improves prologue scheduling.
        target_compile_options(mcla_recomp PRIVATE
            $<$<CONFIG:Release,RelWithDebInfo>:${_mcla_android_opt_flags}>
            -fno-unwind-tables
            -fno-asynchronous-unwind-tables)
    endif()
    target_link_options(mcla PRIVATE
        "-Wl,-Bsymbolic-functions"
        "-Wl,--gc-sections"
        "-Wl,-O2")

    get_filename_component(_mcla_compiler_dir "${CMAKE_CXX_COMPILER}" DIRECTORY)
    find_program(MCLA_STRIP NAMES llvm-strip strip HINTS "${_mcla_compiler_dir}")
    if(MCLA_STRIP)
        add_custom_command(TARGET mcla POST_BUILD
            COMMAND $<$<CONFIG:Release>:${MCLA_STRIP}> $<$<CONFIG:Release>:--strip-unneeded> $<$<CONFIG:Release>:$<TARGET_FILE:mcla>>
            COMMENT "Stripping unneeded symbols from libmain.so"
            VERBATIM)
    endif()
else()
    rexglue_setup_target(mcla)
endif()

if(MINGW)
    # llvm-mingw leaves the DWARF line tables of the generated code
    # (-gline-tables-only, from generated/rexglue.cmake) in the exe: 37 MB of
    # its 81, and the symbol table 6 more. They are split off into
    # mcla.exe.debug beside it (llvm-symbolizer reads them with the exe's
    # address; the exe names the file in a debuglink) and the exe is stripped.
    # Nothing reads them at run time (src/crash_trace.cpp needs no symbols),
    # and the MSVC build drops its line tables at link, too.
    get_filename_component(_mcla_compiler_dir "${CMAKE_CXX_COMPILER}" DIRECTORY)
    find_program(MCLA_OBJCOPY NAMES llvm-objcopy objcopy HINTS "${_mcla_compiler_dir}")
    if(MCLA_OBJCOPY)
        add_custom_command(TARGET mcla POST_BUILD
            COMMAND "${MCLA_OBJCOPY}" --only-keep-debug "$<TARGET_FILE:mcla>" "$<TARGET_FILE:mcla>.debug"
            COMMAND "${MCLA_OBJCOPY}" --strip-all "$<TARGET_FILE:mcla>"
            COMMAND "${MCLA_OBJCOPY}" "--add-gnu-debuglink=$<TARGET_FILE:mcla>.debug" "$<TARGET_FILE:mcla>"
            COMMENT "Splitting the debug information off mcla.exe"
            VERBATIM)
    else()
        message(WARNING "llvm-objcopy was not found: mcla.exe keeps its debug information (80 MB)")
    endif()
endif()

if(WIN32)
    # timeBeginPeriod for the frame limiter.
    target_link_libraries(mcla PRIVATE winmm)
    # The shader storage: the display drivers in its stamp, a preparation's memory figures (dxgi, psapi).
    # The GPU choice (src/mcla_app.cpp, ChooseGpu): Windows' graphics settings are read from the registry (advapi32).
    target_link_libraries(mcla PRIVATE dxgi psapi advapi32)
endif()

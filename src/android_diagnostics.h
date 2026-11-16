#pragma once
#include <cstdint>

#if defined(MCLA_ADRENO_DIAGNOSTIC_APK)
namespace mcla::diagnostics {
void Start();
void Stop();
void RecordUpdate();
void RecordSwap();
void RecordUiPaint();
void RecordStartPress();
void RecordPlayerCar(uint32_t car);
void RecordCameraScriptRequest();
}  // namespace mcla::diagnostics
#endif

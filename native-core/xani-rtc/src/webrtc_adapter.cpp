// libwebrtc adapter. Built only when XANI_RTC_WEBRTC_DIR points at a libwebrtc
// checkout; the policy core above it never includes a webrtc header.
//
// Phase B lands the PeerConnectionFactory here (Opus FEC/DTX, H.264 hardware
// via AMediaCodec / VideoToolbox, ICE restart driven by CallFsm::Step). Until
// then the Kotlin/Swift engines bind libwebrtc's own platform SDK directly,
// which is the same engine with the same behaviour.
#if defined(XANI_RTC_HAVE_WEBRTC)
#include "xani/rtc/call_policy.h"
namespace xani::rtc {
static_assert(sizeof(CallFsm) > 0, "policy core linked");
}
#endif

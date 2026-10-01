// Call policy shared by Android and iOS. Header is C++17 with no allocations
// on the hot path so it can sit behind JNI and ObjC++ without a bridge layer.
#pragma once

#include <cstdint>

namespace xani::rtc {

// Mirrors the server's participant/call state; the clients render exactly this.
enum class Phase : uint8_t { Idle, Outgoing, Incoming, Connecting, Connected, Reconnecting, Ended };

enum class Outcome : uint8_t { None, Ended, Busy, NoAnswer, Declined, Missed, Lost, Failed, Unavailable };

// What the transport reports; identical to RTCPeerConnectionState.
enum class Link : uint8_t { New, Connecting, Connected, Disconnected, Failed, Closed };

struct Limits {
    uint32_t ring_timeout_ms = 60'000;
    uint32_t reconnect_grace_ms = 150'000;  // server-issued; clamp 30 s–10 min
    uint32_t restart_base_ms = 1'500;
    uint32_t restart_max_ms = 8'000;
};

// Deterministic call state machine. Every transition is a pure function of
// (state, input, now_ms); side effects are returned as flags for the platform.
class CallFsm {
public:
    struct Step {
        Phase phase;
        Outcome outcome;
        bool start_grace;     // arm the reconnect grace timer
        bool clear_grace;
        bool restart_ice;     // offerer only
        bool nudge_offerer;   // answerer only: ask the offerer to restart
        uint32_t restart_in_ms;
    };

    explicit CallFsm(Limits limits, bool outgoing, bool sfu) noexcept;

    Phase phase() const noexcept { return phase_; }
    Outcome outcome() const noexcept { return outcome_; }

    Step dial(uint64_t now_ms) noexcept;
    Step ring(uint64_t now_ms) noexcept;
    Step accepted(uint64_t now_ms) noexcept;     // remote accepted / we accepted
    Step link(Link l, uint64_t now_ms) noexcept;
    Step tick(uint64_t now_ms) noexcept;         // timers: ring timeout, grace expiry, backoff
    Step remote_end(Outcome why) noexcept;
    Step local_end() noexcept;

private:
    Step none() const noexcept;
    Step finish(Outcome why) noexcept;

    Limits limits_;
    bool outgoing_;
    bool sfu_;
    Phase phase_ = Phase::Idle;
    Outcome outcome_ = Outcome::None;
    uint64_t ring_deadline_ms_ = 0;
    uint64_t grace_deadline_ms_ = 0;
    uint64_t restart_at_ms_ = 0;
    uint32_t restarts_ = 0;
};

// Video send ladder for village networks. Feed it the last RTT/loss sample;
// it returns the bitrate cap and resolution rung, hysteresis included.
struct Rung {
    uint16_t width;
    uint16_t height;
    uint8_t fps;
    uint32_t max_kbps;
};

class BitrateLadder {
public:
    // Steps down on loss > 8% or RTT > 600 ms; steps up after 6 s of clean samples.
    Rung update(uint32_t rtt_ms, float loss_fraction, uint32_t available_kbps, uint64_t now_ms) noexcept;
    Rung current() const noexcept;

private:
    int rung_ = 2;              // start at 480p
    uint64_t clean_since_ms_ = 0;
};

}  // namespace xani::rtc

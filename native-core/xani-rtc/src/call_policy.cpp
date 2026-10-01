#include "xani/rtc/call_policy.h"

#include <algorithm>

namespace xani::rtc {

namespace {
constexpr uint32_t kGraceMin = 30'000;
constexpr uint32_t kGraceMax = 600'000;
constexpr uint32_t kCleanForUpgradeMs = 6'000;

constexpr Rung kRungs[] = {
    {160, 120, 10, 60},    // 0: audio-first video, ~8 kB/s
    {320, 240, 15, 200},   // 1
    {640, 480, 24, 600},   // 2: default
    {960, 540, 30, 1'200}, // 3
    {1280, 720, 30, 2'000},// 4
};
constexpr int kTopRung = 4;
}  // namespace

CallFsm::CallFsm(Limits limits, bool outgoing, bool sfu) noexcept
    : limits_(limits), outgoing_(outgoing), sfu_(sfu) {
    limits_.reconnect_grace_ms = std::clamp(limits_.reconnect_grace_ms, kGraceMin, kGraceMax);
}

CallFsm::Step CallFsm::none() const noexcept { return {phase_, outcome_, false, false, false, false, 0}; }

CallFsm::Step CallFsm::finish(Outcome why) noexcept {
    // A ring that ends before we answered is a missed call: no lingering surface.
    if (phase_ == Phase::Incoming && why != Outcome::Failed && why != Outcome::Unavailable) why = Outcome::Missed;
    phase_ = Phase::Ended;
    outcome_ = why;
    Step s = none();
    s.clear_grace = true;
    return s;
}

CallFsm::Step CallFsm::dial(uint64_t now_ms) noexcept {
    if (phase_ != Phase::Idle) return none();
    phase_ = Phase::Outgoing;
    ring_deadline_ms_ = now_ms + limits_.ring_timeout_ms;
    return none();
}

CallFsm::Step CallFsm::ring(uint64_t now_ms) noexcept {
    if (phase_ != Phase::Idle) return none();
    phase_ = Phase::Incoming;
    ring_deadline_ms_ = now_ms + limits_.ring_timeout_ms;
    return none();
}

CallFsm::Step CallFsm::accepted(uint64_t) noexcept {
    if (phase_ != Phase::Outgoing && phase_ != Phase::Incoming) return none();
    phase_ = Phase::Connecting;
    ring_deadline_ms_ = 0;
    return none();
}

CallFsm::Step CallFsm::link(Link l, uint64_t now_ms) noexcept {
    if (phase_ == Phase::Ended || phase_ == Phase::Idle) return none();
    switch (l) {
        case Link::Connected: {
            phase_ = Phase::Connected;
            grace_deadline_ms_ = 0;
            restart_at_ms_ = 0;
            restarts_ = 0;
            Step s = none();
            s.clear_grace = true;
            return s;
        }
        case Link::Disconnected:
        case Link::Failed: {
            if (phase_ == Phase::Outgoing || phase_ == Phase::Incoming) return none();
            phase_ = Phase::Reconnecting;
            Step s = none();
            if (grace_deadline_ms_ == 0) {
                grace_deadline_ms_ = now_ms + limits_.reconnect_grace_ms;
                s.start_grace = true;
            }
            if (restart_at_ms_ == 0) {
                const uint32_t wait = (l == Link::Failed && restarts_ == 0) ? 0 : std::min(limits_.restart_base_ms << std::min<uint32_t>(restarts_, 2), limits_.restart_max_ms);
                restart_at_ms_ = now_ms + wait;
                s.restart_in_ms = wait;
            }
            return s;
        }
        case Link::Closed:
            if (phase_ == Phase::Connected || phase_ == Phase::Reconnecting) return finish(Outcome::Lost);
            return none();
        case Link::New:
        case Link::Connecting:
            return none();
    }
    return none();
}

CallFsm::Step CallFsm::tick(uint64_t now_ms) noexcept {
    if (ring_deadline_ms_ != 0 && now_ms >= ring_deadline_ms_) {
        ring_deadline_ms_ = 0;
        if (phase_ == Phase::Outgoing) return finish(Outcome::NoAnswer);
        if (phase_ == Phase::Incoming) return finish(Outcome::Missed);
    }
    if (phase_ == Phase::Reconnecting) {
        if (grace_deadline_ms_ != 0 && now_ms >= grace_deadline_ms_) return finish(Outcome::Lost);
        if (restart_at_ms_ != 0 && now_ms >= restart_at_ms_) {
            ++restarts_;
            Step s = none();
            if (!sfu_) {
                if (outgoing_) s.restart_ice = true; else s.nudge_offerer = true;
            }
            const uint32_t wait = std::min(limits_.restart_base_ms << std::min<uint32_t>(restarts_, 2), limits_.restart_max_ms);
            restart_at_ms_ = now_ms + wait;
            s.restart_in_ms = wait;
            return s;
        }
    }
    return none();
}

CallFsm::Step CallFsm::remote_end(Outcome why) noexcept {
    if (phase_ == Phase::Ended || phase_ == Phase::Idle) return none();
    return finish(why);
}

CallFsm::Step CallFsm::local_end() noexcept {
    if (phase_ == Phase::Ended || phase_ == Phase::Idle) return none();
    phase_ = Phase::Ended;
    outcome_ = Outcome::Ended;
    Step s = none();
    s.clear_grace = true;
    return s;
}

Rung BitrateLadder::current() const noexcept { return kRungs[rung_]; }

Rung BitrateLadder::update(uint32_t rtt_ms, float loss_fraction, uint32_t available_kbps, uint64_t now_ms) noexcept {
    const bool bad = loss_fraction > 0.08f || rtt_ms > 600 || (available_kbps != 0 && available_kbps < kRungs[rung_].max_kbps / 2);
    if (bad) {
        rung_ = std::max(0, rung_ - 1);
        clean_since_ms_ = 0;
        return kRungs[rung_];
    }
    if (clean_since_ms_ == 0) clean_since_ms_ = now_ms;
    const bool room_above = rung_ < kTopRung && (available_kbps == 0 || available_kbps >= kRungs[rung_ + 1].max_kbps);
    if (room_above && now_ms - clean_since_ms_ >= kCleanForUpgradeMs) {
        ++rung_;
        clean_since_ms_ = now_ms;
    }
    return kRungs[rung_];
}

}  // namespace xani::rtc

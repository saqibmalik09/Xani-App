#include "xani/rtc/call_policy.h"

#include <cstdio>
#include <cstdlib>

using namespace xani::rtc;

#define CHECK(x) do { if (!(x)) { std::fprintf(stderr, "FAIL %s:%d %s\n", __FILE__, __LINE__, #x); std::exit(1); } } while (0)

static void outgoing_no_answer() {
    CallFsm f(Limits{}, /*outgoing=*/true, /*sfu=*/false);
    f.dial(0);
    CHECK(f.phase() == Phase::Outgoing);
    f.tick(59'999);
    CHECK(f.phase() == Phase::Outgoing);
    f.tick(60'000);
    CHECK(f.phase() == Phase::Ended && f.outcome() == Outcome::NoAnswer);
}

static void reconnect_within_grace() {
    CallFsm f(Limits{}, true, false);
    f.dial(0); f.accepted(1'000); f.link(Link::Connected, 2'000);
    CHECK(f.phase() == Phase::Connected);
    auto s = f.link(Link::Disconnected, 10'000);
    CHECK(f.phase() == Phase::Reconnecting && s.start_grace && s.restart_in_ms == 1'500);
    s = f.tick(11'500);
    CHECK(s.restart_ice && !s.nudge_offerer);
    f.link(Link::Connected, 12'000);
    CHECK(f.phase() == Phase::Connected);
    // Grace no longer applies after a clean reconnect.
    f.tick(200'000);
    CHECK(f.phase() == Phase::Connected);
}

static void grace_expires_to_lost() {
    CallFsm f(Limits{}, false, false);
    f.ring(0); f.accepted(500); f.link(Link::Connected, 1'000);
    auto s = f.link(Link::Failed, 5'000);
    CHECK(s.restart_in_ms == 0);
    s = f.tick(5'000);
    CHECK(s.nudge_offerer && !s.restart_ice);
    f.tick(5'000 + 149'999);
    CHECK(f.phase() == Phase::Reconnecting);
    f.tick(5'000 + 150'000);
    CHECK(f.phase() == Phase::Ended && f.outcome() == Outcome::Lost);
}

static void incoming_cancelled_is_missed() {
    CallFsm f(Limits{}, false, false);
    f.ring(0);
    f.remote_end(Outcome::Ended);
    CHECK(f.outcome() == Outcome::Missed);
}

static void ladder_steps() {
    BitrateLadder l;
    CHECK(l.current().height == 480);
    CHECK(l.update(900, 0.0f, 0, 0).height == 240);
    CHECK(l.update(100, 0.2f, 0, 100).height == 120);
    CHECK(l.update(100, 0.0f, 0, 200).height == 120);      // clean, not long enough
    CHECK(l.update(100, 0.0f, 0, 6'300).height == 240);    // 6 s clean → up one
    CHECK(l.update(100, 0.0f, 300, 12'400).height == 240); // not enough headroom for 480p
}

int main() {
    outgoing_no_answer();
    reconnect_within_grace();
    grace_expires_to_lost();
    incoming_cancelled_is_missed();
    ladder_steps();
    std::puts("ok");
    return 0;
}

cmake_minimum_required(VERSION 3.22)
project(xani_rtc LANGUAGES CXX)

# Shared call-policy core for Android (JNI) and iOS (ObjC++). Pure C++17, no
# platform or libwebrtc dependency: the state machine, reconnect backoff and
# the weak-network bitrate ladder live here so both apps behave identically.
#
# Set XANI_RTC_WEBRTC_DIR to a libwebrtc checkout (out/<target>) to build the
# media adapter as well; without it only the policy core and tests are built.

option(XANI_RTC_BUILD_TESTS "Build policy unit tests" ON)
set(XANI_RTC_WEBRTC_DIR "" CACHE PATH "Prebuilt libwebrtc root (optional)")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)

add_library(xani_rtc_core STATIC
    src/call_policy.cpp
)
target_include_directories(xani_rtc_core PUBLIC include)
target_compile_options(xani_rtc_core PRIVATE -Wall -Wextra -Werror -fno-exceptions -fno-rtti)

if(XANI_RTC_WEBRTC_DIR)
    add_library(xani_rtc SHARED src/webrtc_adapter.cpp)
    target_link_libraries(xani_rtc PRIVATE xani_rtc_core)
    target_include_directories(xani_rtc PRIVATE
        ${XANI_RTC_WEBRTC_DIR}/include
        ${XANI_RTC_WEBRTC_DIR}/include/third_party/abseil-cpp)
    target_compile_definitions(xani_rtc PRIVATE XANI_RTC_HAVE_WEBRTC=1 WEBRTC_POSIX=1)
    if(ANDROID)
        target_compile_definitions(xani_rtc PRIVATE WEBRTC_ANDROID=1 WEBRTC_LINUX=1)
        target_link_libraries(xani_rtc PRIVATE ${XANI_RTC_WEBRTC_DIR}/lib/${ANDROID_ABI}/libwebrtc.a log OpenSLES)
    elseif(APPLE)
        target_compile_definitions(xani_rtc PRIVATE WEBRTC_MAC=1 WEBRTC_IOS=1)
        target_link_libraries(xani_rtc PRIVATE ${XANI_RTC_WEBRTC_DIR}/lib/libwebrtc.a)
    endif()
endif()

if(XANI_RTC_BUILD_TESTS AND NOT ANDROID AND NOT IOS)
    enable_testing()
    add_executable(call_policy_test tests/call_policy_test.cpp)
    target_link_libraries(call_policy_test PRIVATE xani_rtc_core)
    add_test(NAME call_policy COMMAND call_policy_test)
endif()

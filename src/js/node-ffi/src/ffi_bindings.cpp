#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#include <napi.h>
#include <cstdint>
#include <cstddef>
#include <cstdlib>
#include <cstring>
#include <cerrno>
#include <string>
#ifdef _WIN32
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#else
#include <dlfcn.h>
#ifndef RTLD_DEFAULT
#define RTLD_DEFAULT ((void*)0)
#endif
#endif

#include "crossbeam_platform.h"

struct KmNapiRegistrarNode {
    void (*register_fn)(Napi::Env, Napi::Object);
    KmNapiRegistrarNode* next;
};
#if defined(__GNUC__) || defined(__clang__)
__attribute__((visibility("hidden")))
#endif
extern KmNapiRegistrarNode* km_napi_registrar_head;

namespace {

static int64_t km_napi_arg_i64(const Napi::Value& v) {
    if (v.IsNull() || v.IsUndefined()) return 0;
    if (v.IsString()) {
        std::string s = v.As<Napi::String>().Utf8Value();
        return static_cast<int64_t>(std::strtoll(s.c_str(), nullptr, 10));
    }
    if (v.IsNumber()) return v.As<Napi::Number>().Int64Value();
    return 0;
}

static uint64_t km_napi_arg_u64(const Napi::Value& v) {
    if (v.IsNull() || v.IsUndefined()) return 0;
    if (v.IsString()) {
        std::string s = v.As<Napi::String>().Utf8Value();
        return static_cast<uint64_t>(std::strtoull(s.c_str(), nullptr, 10));
    }
    if (v.IsNumber()) return static_cast<uint64_t>(v.As<Napi::Number>().Int64Value());
    return 0;
}

static uint64_t crossbeam_utils_platform_ffi_inline_0_crossbeam_thread_id(void) {
    return crossbeam_platform_thread_id();
}

static Napi::Value crossbeam_utils_platform_ffi_inline_0_crossbeam_thread_id_napi(const Napi::CallbackInfo& km_info) {
    Napi::Env km_env = km_info.Env();
    uint64_t km_res = crossbeam_utils_platform_ffi_inline_0_crossbeam_thread_id();
    return Napi::String::New(km_env, std::to_string(static_cast<uint64_t>(km_res)));
}

static void crossbeam_utils_platform_ffi_inline_1_crossbeam_spin_loop(void) {
    crossbeam_platform_spin_loop();
}

static Napi::Value crossbeam_utils_platform_ffi_inline_1_crossbeam_spin_loop_napi(const Napi::CallbackInfo& km_info) {
    Napi::Env km_env = km_info.Env();
    crossbeam_utils_platform_ffi_inline_1_crossbeam_spin_loop();
    return km_env.Undefined();
}

static void crossbeam_utils_platform_ffi_inline_2_crossbeam_yield_thread(void) {
    crossbeam_platform_yield_thread();
}

static Napi::Value crossbeam_utils_platform_ffi_inline_2_crossbeam_yield_thread_napi(const Napi::CallbackInfo& km_info) {
    Napi::Env km_env = km_info.Env();
    crossbeam_utils_platform_ffi_inline_2_crossbeam_yield_thread();
    return km_env.Undefined();
}

static void register_utils_platform_ffi_napi(Napi::Env env, Napi::Object exports) {
    exports.Set("crossbeam_utils_platform_ffi_inline_0_crossbeam_thread_id", Napi::Function::New(env, crossbeam_utils_platform_ffi_inline_0_crossbeam_thread_id_napi));
    exports.Set("crossbeam_utils_platform_ffi_inline_1_crossbeam_spin_loop", Napi::Function::New(env, crossbeam_utils_platform_ffi_inline_1_crossbeam_spin_loop_napi));
    exports.Set("crossbeam_utils_platform_ffi_inline_2_crossbeam_yield_thread", Napi::Function::New(env, crossbeam_utils_platform_ffi_inline_2_crossbeam_yield_thread_napi));
}

static KmNapiRegistrarNode registrar_node = { &register_utils_platform_ffi_napi, nullptr };
struct KmNapiAutoRegister {
    KmNapiAutoRegister() {
        registrar_node.next = km_napi_registrar_head;
        km_napi_registrar_head = &registrar_node;
    }
};
static KmNapiAutoRegister auto_register_instance;

} // namespace

#ifdef KM_NAPI_STANDALONE_MODULE
#if defined(__GNUC__) || defined(__clang__)
__attribute__((visibility("hidden")))
#endif
KmNapiRegistrarNode* km_napi_registrar_head = nullptr;
static Napi::Object KmNapiStandaloneInit(Napi::Env env, Napi::Object exports) {
    for (KmNapiRegistrarNode* cur = km_napi_registrar_head; cur != nullptr; cur = cur->next) {
        cur->register_fn(env, exports);
    }
    return exports;
}
NODE_API_MODULE(NODE_GYP_MODULE_NAME, KmNapiStandaloneInit)
#endif

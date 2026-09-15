#include <jni.h>
#include <whisper.h>

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <cstring>
#include <memory>
#include <string>
#include <thread>
#include <vector>

namespace {
struct Engine {
    explicit Engine(whisper_context * value) : context(value) { }
    ~Engine() { whisper_free(context); }
    whisper_context * context;
    std::atomic<bool> cancelled{false};
};

Engine * engine(jlong handle) {
    return reinterpret_cast<Engine *>(static_cast<intptr_t>(handle));
}

bool abort_decode(void * data) {
    return static_cast<Engine *>(data)->cancelled.load();
}

bool allowed_language(const std::string & language) {
    return language == "km" || language == "en" || language == "th" || language == "zh";
}
} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_app_morphe_extension_swiftkey_voice_WhisperEngine_nativeCreate(
        JNIEnv * env, jclass, jstring path) {
    if (!path) return 0;
    const char * utf_path = env->GetStringUTFChars(path, nullptr);
    if (!utf_path) return 0;
    whisper_context * context = nullptr;
    try {
        // No transcript/model diagnostics in logcat. The Java UI reports actionable errors.
        whisper_log_set([](ggml_log_level, const char *, void *) { }, nullptr);
        auto parameters = whisper_context_default_params();
        parameters.use_gpu = false;
        context = whisper_init_from_file_with_params(utf_path, parameters);
        env->ReleaseStringUTFChars(path, utf_path);
        utf_path = nullptr;
        if (!context) return 0;
        std::unique_ptr<Engine> instance(new Engine(context));
        context = nullptr;
        if (!whisper_is_multilingual(instance->context)) return 0;
        return static_cast<jlong>(reinterpret_cast<intptr_t>(instance.release()));
    } catch (...) {
        if (utf_path) env->ReleaseStringUTFChars(path, utf_path);
        if (context) whisper_free(context);
        return 0;
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_app_morphe_extension_swiftkey_voice_WhisperEngine_nativeTranscribe(
        JNIEnv * env, jclass, jlong handle, jfloatArray audio, jstring language) {
    Engine * instance = engine(handle);
    if (!instance || !audio || !language || instance->cancelled.load()) return nullptr;
    const jsize count = env->GetArrayLength(audio);
    if (count <= 0 || count > 16000 * 30) return nullptr;
    const char * code = env->GetStringUTFChars(language, nullptr);
    if (!code) return nullptr;
    try {
        const std::string selected(code);
        env->ReleaseStringUTFChars(language, code);
        code = nullptr;
        if (!allowed_language(selected)) return nullptr;
        std::vector<float> samples(static_cast<size_t>(count));
        env->GetFloatArrayRegion(audio, 0, count, samples.data());
        if (env->ExceptionCheck()) return nullptr;

        auto parameters = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        parameters.n_threads = std::max(1u, std::min(4u, std::thread::hardware_concurrency()));
        parameters.language = selected.c_str();
        parameters.translate = false;
        parameters.no_context = true;
        parameters.no_timestamps = true;
        parameters.print_special = false;
        parameters.print_progress = false;
        parameters.print_realtime = false;
        parameters.print_timestamps = false;
        parameters.abort_callback = abort_decode;
        parameters.abort_callback_user_data = instance;
        if (whisper_full(instance->context, parameters, samples.data(), count) != 0
                || instance->cancelled.load()) return nullptr;

        std::string text;
        for (int i = 0; i < whisper_full_n_segments(instance->context); ++i) {
            const char * segment = whisper_full_get_segment_text(instance->context, i);
            if (segment) text += segment;
        }
        jbyteArray result = env->NewByteArray(static_cast<jsize>(text.size()));
        if (result) env->SetByteArrayRegion(result, 0, static_cast<jsize>(text.size()),
            reinterpret_cast<const jbyte *>(text.data()));
        return result;
    } catch (...) {
        if (code) env->ReleaseStringUTFChars(language, code);
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_app_morphe_extension_swiftkey_voice_WhisperEngine_nativeCancel(
        JNIEnv *, jclass, jlong handle) {
    if (engine(handle)) engine(handle)->cancelled.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_app_morphe_extension_swiftkey_voice_WhisperEngine_nativeFree(
        JNIEnv *, jclass, jlong handle) {
    delete engine(handle);
}

#include <android/bitmap.h>
#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <vector>

namespace {

struct Pixel {
    uint8_t red;
    uint8_t green;
    uint8_t blue;
    uint8_t alpha;
};

static_assert(sizeof(Pixel) == 4, "RGBA_8888 pixels must be four bytes");

struct ChannelSums {
    int red = 0;
    int green = 0;
    int blue = 0;
};

int clampIndex(int value, int length) {
    return std::clamp(value, 0, length - 1);
}

uint8_t clampChannel(float value) {
    return static_cast<uint8_t>(std::clamp(value, 0.0f, 255.0f) + 0.5f);
}

void add(ChannelSums& sums, const Pixel& pixel, int sign) {
    sums.red += sign * pixel.red;
    sums.green += sign * pixel.green;
    sums.blue += sign * pixel.blue;
}

Pixel average(const ChannelSums& sums, int count) {
    return {
        static_cast<uint8_t>(sums.red / count),
        static_cast<uint8_t>(sums.green / count),
        static_cast<uint8_t>(sums.blue / count),
        255,
    };
}

void horizontalBlur(
    const std::vector<Pixel>& source,
    std::vector<Pixel>& target,
    int width,
    int height,
    int radius
) {
    const int window = radius * 2 + 1;
    for (int y = 0; y < height; ++y) {
        const int row = y * width;
        ChannelSums sums;
        for (int offset = -radius; offset <= radius; ++offset) {
            add(sums, source[row + clampIndex(offset, width)], 1);
        }
        for (int x = 0; x < width; ++x) {
            target[row + x] = average(sums, window);
            add(sums, source[row + clampIndex(x - radius, width)], -1);
            add(sums, source[row + clampIndex(x + radius + 1, width)], 1);
        }
    }
}

void verticalBlur(
    const std::vector<Pixel>& source,
    std::vector<Pixel>& target,
    int width,
    int height,
    int radius
) {
    const int window = radius * 2 + 1;
    for (int x = 0; x < width; ++x) {
        ChannelSums sums;
        for (int offset = -radius; offset <= radius; ++offset) {
            add(sums, source[clampIndex(offset, height) * width + x], 1);
        }
        for (int y = 0; y < height; ++y) {
            target[y * width + x] = average(sums, window);
            add(sums, source[clampIndex(y - radius, height) * width + x], -1);
            add(sums, source[clampIndex(y + radius + 1, height) * width + x], 1);
        }
    }
}

void blurAndGrade(
    const Pixel* sourcePixels,
    Pixel* targetPixels,
    int width,
    int height,
    int radius,
    float saturation,
    float brightness
) {
    const size_t pixelCount = static_cast<size_t>(width) * static_cast<size_t>(height);
    std::vector<Pixel> current(sourcePixels, sourcePixels + pixelCount);
    std::vector<Pixel> horizontal(pixelCount);
    std::vector<Pixel> next(pixelCount);

    // Three box passes approximate the broad Gaussian used by the desktop lyrics artwork.
    for (int pass = 0; pass < 3; ++pass) {
        horizontalBlur(current, horizontal, width, height, radius);
        verticalBlur(horizontal, next, width, height, radius);
        current.swap(next);
    }

    for (size_t index = 0; index < pixelCount; ++index) {
        const Pixel& pixel = current[index];
        const float luminance = 0.2126f * pixel.red + 0.7152f * pixel.green + 0.0722f * pixel.blue;
        targetPixels[index] = {
            clampChannel((luminance + (pixel.red - luminance) * saturation) * brightness),
            clampChannel((luminance + (pixel.green - luminance) * saturation) * brightness),
            clampChannel((luminance + (pixel.blue - luminance) * saturation) * brightness),
            255,
        };
    }
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_darkxvenom_itune_ui_player_DynamicArtworkProcessor_blurAndGrade(
    JNIEnv* env,
    jobject,
    jobject sourceBitmap,
    jobject targetBitmap,
    jint radius,
    jfloat saturation,
    jfloat brightness
) {
    AndroidBitmapInfo sourceInfo{};
    AndroidBitmapInfo targetInfo{};
    if (AndroidBitmap_getInfo(env, sourceBitmap, &sourceInfo) != ANDROID_BITMAP_RESULT_SUCCESS ||
        AndroidBitmap_getInfo(env, targetBitmap, &targetInfo) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return JNI_FALSE;
    }
    if (sourceInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        targetInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        sourceInfo.width != targetInfo.width || sourceInfo.height != targetInfo.height) {
        return JNI_FALSE;
    }

    void* sourcePixels = nullptr;
    void* targetPixels = nullptr;
    if (AndroidBitmap_lockPixels(env, sourceBitmap, &sourcePixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        return JNI_FALSE;
    }
    if (AndroidBitmap_lockPixels(env, targetBitmap, &targetPixels) != ANDROID_BITMAP_RESULT_SUCCESS) {
        AndroidBitmap_unlockPixels(env, sourceBitmap);
        return JNI_FALSE;
    }

    const int width = static_cast<int>(sourceInfo.width);
    const int height = static_cast<int>(sourceInfo.height);
    std::vector<Pixel> source(static_cast<size_t>(width) * height);
    for (int y = 0; y < height; ++y) {
        const auto* row = reinterpret_cast<const Pixel*>(
            static_cast<const uint8_t*>(sourcePixels) + y * sourceInfo.stride
        );
        std::copy(row, row + width, source.begin() + y * width);
    }

    std::vector<Pixel> output(static_cast<size_t>(width) * height);
    blurAndGrade(source.data(), output.data(), width, height,
                 std::clamp(static_cast<int>(radius), 1, 40), saturation, brightness);
    for (int y = 0; y < height; ++y) {
        auto* row = reinterpret_cast<Pixel*>(
            static_cast<uint8_t*>(targetPixels) + y * targetInfo.stride
        );
        std::copy(output.begin() + y * width, output.begin() + (y + 1) * width, row);
    }

    AndroidBitmap_unlockPixels(env, targetBitmap);
    AndroidBitmap_unlockPixels(env, sourceBitmap);
    return JNI_TRUE;
}

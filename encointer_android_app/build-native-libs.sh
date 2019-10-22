#!/bin/sh
cd encointer-api-native
echo "building for i686"
#export NDK_TOOLCHAIN=~/Android/Sdk/ndk/toolchains/llvm/prebuilt/linux-x86_64/bin
export CC=i686-linux-android16-clang
cargo +nightly build --target i686-linux-android --release
echo "building for armv7"
export CC=armv7a-linux-androideabi16-clang
cargo +nightly build --target armv7-linux-androideabi --release
echo "building for aarch64"
export CC=aarch64-linux-android21-clang
cargo +nightly build --target aarch64-linux-android --release

echo "copying libs to android project"
JNI_LIBS=../app/src/main/jniLibs
rm -rf $JNI_LIBS
mkdir $JNI_LIBS
mkdir $JNI_LIBS/arm64-v8a
mkdir $JNI_LIBS/armeabi-v7a
mkdir $JNI_LIBS/x86

cp target/aarch64-linux-android/release/libencointer_api_native.so $JNI_LIBS/arm64-v8a/libencointer_api_native.so
cp target/armv7-linux-androideabi/release/libencointer_api_native.so $JNI_LIBS/armeabi-v7a/libencointer_api_native.so
cp target/i686-linux-android/release/libencointer_api_native.so $JNI_LIBS/x86/libencointer_api_native.so

#!/bin/sh

cd encointer-api-native
echo "building for i686"
cargo +nightly build --target i686-linux-android --release
echo "building for armv7"
cargo +nightly build --target armv7-linux-androideabi --release
echo "building for aarch64"
cargo +nighty build --target aarch64-linux-android --release

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

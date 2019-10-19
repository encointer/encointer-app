
cd encointer-api-native
echo "building for i686"
cargo +nightly build --target i686-linux-android --release
echo "building for armv7"
cargo +nightly build --target armv7-linux-androideabi --release
echo "building for aarch64"
cargo +nightly build --target aarch64-linux-android --release

echo "copying libs to android project"
set JNI_LIBS=../app/src/main/jniLibs
rd /s /q $JNI_LIBS
mkdir $JNI_LIBS
mkdir $JNI_LIBS/arm64-v8a
mkdir $JNI_LIBS/armeabi-v7a
mkdir $JNI_LIBS/x86

copy target/aarch64-linux-android/release/libencointer_api_native.so $JNI_LIBS/arm64-v8a/libencointer_api_native.so
copy target/armv7-linux-androideabi/release/libencointer_api_native.so $JNI_LIBS/armeabi-v7a/libencointer_api_native.so
copy target/i686-linux-android/release/libencointer_api_native.so $JNI_LIBS/x86/libencointer_api_native.so

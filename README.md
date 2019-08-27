# encointer-app
mobile phone app for encointer ceremonies and wallet

# Build Instructions
1. Install [Android Studio](https://developer.android.com/studio) (We will assume the SDK is installed to `~/Android/Sdk`)
1. Install NDK from Tools -> SDK Manager -> Tab: SDK Tools -> NDK (we will assume the NDK is installed to `~/Android/Sdk/ndk`)
1. install rust toolchain: 
   ```
   curl https://sh.rustup.rs -sSf | sh
   ```
1. add toolchains
   ```
   rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android
   ```
1. add the following to `~/.cargo/config`
   ```
   [target.aarch64-linux-android]
   ar = "/home/<user>/Android/Sdk/ndk/20.0.5594570/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android-ar"
   linker = "/home/<user>/Android/Sdk/ndk/20.0.5594570/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang"
   
   [target.armv7-linux-androideabi]
   ar = "/home/<user>/Android/Sdk/ndk/20.0.5594570/toolchains/llvm/prebuilt/linux-x86_64/bin/arm-linux-androideabi-ar"
   linker = "/home/<user>/Android/Sdk/ndk/20.0.5594570/toolchains/llvm/prebuilt/linux-x86_64/bin/armv7a-linux-androideabi26-clang"
   
   [target.i686-linux-android]
   ar = "/home/<user>/Android/Sdk/ndk/20.0.5594570/toolchains/llvm/prebuilt/linux-x86_64/bin/i686-linux-android-ar"
   linker = "/home/<user>/Android/Sdk/ndk/20.0.5594570/toolchains/llvm/prebuilt/linux-x86_64/bin/i686-linux-android26-clang"
   ```
1. build native library
   ```
   ./build-native-libs.sh
   ```   
2. In studio, Choose "Open an existing Anroid Studio project"
3. If needed, sync Gradle file
4. Build
5. Run on Android device (simulator does not work with Android Nearby)



# Android Studio
Version: 3.4

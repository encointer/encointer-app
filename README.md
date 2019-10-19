# encointer-app
mobile phone app for encointer ceremonies and wallet

## Build Instructions
1. Install [Android Studio](https://developer.android.com/studio) (We will assume the SDK is installed to `~/Android/Sdk`)
1. Install NDK from Tools -> SDK Manager -> Tab: SDK Tools -> NDK (we will assume the NDK is installed to `~/Android/Sdk/ndk`)
1. install rust toolchain: 
   ```
   curl https://sh.rustup.rs -sSf | sh
   rustup toolchain install nightly
   ```
1. add toolchains
   ```
   rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android --toolchain nigthly
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
1. fix paths and add symlinks 
   ```
   export PATH=~/Android/Sdk/ndk/20.0.5594570/toolchains/llvm/prebuilt/linux-x86_64/bin:$PATH
   cd ~/Android/Sdk/ndk/20.0.5594570/toolchains/llvm/prebuilt/linux-x86_64/bin/
   ln -s i686-linux-android16-clang i686-linux-android-clang
   ln -s armv7a-linux-androideabi16-clang arm-linux-androideabi-clang
   ln -s aarch64-linux-android21-clang aarch64-linux-android-clang

   ```
   a. if you have trouble building in the next step because of missing gcc, you might find this additional workaround useful. (not sure if linking gcc to clang is legit, but there's no gcc in android anymore and the two are highly compatible)
      ```
      ln -s i686-linux-android16-clang i686-linux-android-gcc
      ln -s armv7a-linux-androideabi16-clang arm-linux-androideabi-gcc
      ln -s aarch64-linux-android21-clang aarch64-linux-android-gcc
      ```

1. build native library
   ```
   ./build-native-libs.sh
   ```   
1. In studio, Choose "Open an existing Anroid Studio project"
1. If needed, sync Gradle file
1. Build
1. Run on Android device (simulator does not work with Android Nearby)


## Demo
### run node
Somewhere in your local WiFi accessible network, run
```
substrate --dev --ws-external
```
We'll assume your node's IP is 192.168.1.4

### send extrinsic from app
...TODO


## Android Studio
Version: 3.4

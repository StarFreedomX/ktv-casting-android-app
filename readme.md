# ktv-casting-for-android

---

本项目是 [ktv-casting](https://github.com/aspromise/ktv-casting) 的安卓前端

目前采用的分支为 [android-app](https://github.com/StarFreedomX/ktv-casting/tree/android-app) 分支

## 环境准备

---

```shell
# 克隆 android-app分支的rust源码
git clone -b android-app --single-branch https://github.com/StarFreedomX/ktv-casting.git

cd ktv-casting

rustup target add aarch64-linux-android
cargo ndk -t arm64-v8a build --lib --release
# 产物输出在 ./target/aarch64-linux-android/release/libktv_casting_lib.so

# 下面是更多版本

# rustup target add armv7-linux-androideabi
# cargo ndk -t armeabi-v7a build --lib --release

# rustup target add i686-linux-android
# cargo ndk -t x86 build --lib --release

# rustup target add x86_64-linux-android
# cargo ndk -t x86_64 build --lib --release

cd ..
```
这样就得到了`libktv_casting_lib.so`

接下来克隆本项目
```shell
git clone https://github.com/StarFreedomX/ktv-casting-android-app.git

cd ktv-casting-android-app
```

打开 [Android Studio](https://developer.android.google.cn/studio?hl=zh-cn) 将文件夹打开为项目

把上一步得到的编译产物放到`app/src/main/jniLibs/arm64-v8a/libktv_casting_lib.so`(这里的架构版本根据上面-t参数选择)

直接在android studio运行调试即可

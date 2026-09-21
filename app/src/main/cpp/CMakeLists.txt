
# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("carlauncher")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
#
# In this top level CMakeLists.txt, ${CMAKE_PROJECT_NAME} is used to define
# the target library name; in the sub-module's CMakeLists.txt, ${PROJECT_NAME}
# is preferred for the same purpose.
#
# In order to load a library into your app from Java/Kotlin, you must call
# System.loadLibrary() and pass the name of the library defined here;
# for GameActivity/NativeActivity derived applications, the same library name must be
# used in the AndroidManifest.xml file.
add_library(${CMAKE_PROJECT_NAME} SHARED
    # List C/C++ source files with relative paths to this CMakeLists.txt.
    carlauncher.cpp)

# CarLauncher: 使用单库（monolithic）libvsomeip3.so，以
# ENABLE_MULTIPLE_ROUTING_MANAGERS=ON 构建（configuration + service discovery 编入核心库，
# 不再 dlopen 插件：bionic 不跨 DSO 统一弱 typeinfo 会让插件 dynamic_cast 失败 → exit）。
set(VSOMEIP_DEPENDENCIES_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party")
set(VSOMEIP_SOURCE_ROOT "${VSOMEIP_DEPENDENCIES_ROOT}/vsomeip" CACHE PATH
    "vSomeIP source submodule")
# Deliberately not a CACHE entry: old IDE caches must not restore the vendored .so.
set(VSOMEIP_LIBRARY_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../build/native/artifact/lib/${ANDROID_ABI}")

if(NOT EXISTS "${VSOMEIP_SOURCE_ROOT}/interface/vsomeip/vsomeip.hpp")
    message(FATAL_ERROR "vSomeIP submodule missing; run git submodule update --init --recursive")
endif()
if(NOT EXISTS "${VSOMEIP_LIBRARY_DIR}/libvsomeip3.so")
    message(FATAL_ERROR "Source-built vSomeIP missing for ${ANDROID_ABI}; run bash tools/vsomeip-android/build-source.sh on Linux, or restore the matching CI native artifact; see third_party/vsomeip-android/README.md")
endif()

add_library(vsomeip3 SHARED IMPORTED)
set_target_properties(vsomeip3 PROPERTIES
    IMPORTED_LOCATION "${VSOMEIP_LIBRARY_DIR}/libvsomeip3.so"
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    "${VSOMEIP_SOURCE_ROOT}/interface"
)

# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(${CMAKE_PROJECT_NAME}
    # List libraries link to the target library
    vsomeip3
    android
    log)

cmake_minimum_required(VERSION 3.22.1)
project(someip_jni LANGUAGES CXX)

get_filename_component(SOMEIP_REPOSITORY_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../.." ABSOLUTE)
set(VSOMEIP_SOURCE_ROOT "${SOMEIP_REPOSITORY_ROOT}/third_party/vsomeip" CACHE PATH
    "Pinned vSomeIP source checkout containing its public interface")
set(VSOMEIP_LIBRARY_DIR "${SOMEIP_REPOSITORY_ROOT}/build/native/artifact/lib/${ANDROID_ABI}" CACHE PATH
    "Directory containing the source-built Android libvsomeip3.so")

if(NOT EXISTS "${VSOMEIP_SOURCE_ROOT}/interface/vsomeip/vsomeip.hpp")
    message(FATAL_ERROR "vSomeIP headers missing; initialize third_party/vsomeip or set VSOMEIP_SOURCE_ROOT")
endif()
if(NOT EXISTS "${VSOMEIP_LIBRARY_DIR}/libvsomeip3.so")
    message(FATAL_ERROR "Source-built vSomeIP missing for ${ANDROID_ABI}; build tools/vsomeip-android/build-source.sh or restore its matching artifact, or set VSOMEIP_LIBRARY_DIR")
endif()

# This Android build uses the monolithic vSomeIP core, including configuration and SD.
add_library(vsomeip3 SHARED IMPORTED)
set_target_properties(vsomeip3 PROPERTIES IMPORTED_LOCATION "${VSOMEIP_LIBRARY_DIR}/libvsomeip3.so")
add_subdirectory(native)
add_library(someip_jni SHARED jni/SomeIpJni.cpp)
target_compile_features(someip_jni PRIVATE cxx_std_17)
target_link_libraries(someip_jni PRIVATE someip_client android log)

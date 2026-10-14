FROM eclipse-temurin:21-jdk-noble@sha256:b468c3fc688b14450571494f588bd939378e7fd542ed5a73f8efc13f17872a87
RUN sed -i 's|http://archive.ubuntu.com|https://archive.ubuntu.com|g; s|http://security.ubuntu.com|https://security.ubuntu.com|g' /etc/apt/sources.list.d/ubuntu.sources \
    && apt-get update && apt-get install -y --no-install-recommends \
    git curl python3 unzip bzip2 build-essential ninja-build ca-certificates \
    && rm -rf /var/lib/apt/lists/*
ENV ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH=/root/.local/bin:${PATH}
RUN curl --fail --silent --show-error --location --retry 3 \
    https://dl.google.com/android/cli/latest/linux_x86_64/install.sh -o /tmp/install-android-cli.sh \
    && bash /tmp/install-android-cli.sh && android --version \
    && mkdir -p "$ANDROID_HOME" \
    && android --sdk="$ANDROID_HOME" sdk install platforms/android-36.1 build-tools/36.0.0 \
       ndk/28.2.13676358 cmake/3.22.1 platform-tools \
    && test -f "$ANDROID_HOME/platforms/android-36.1/optional/android.car.jar" \
    && rm /tmp/install-android-cli.sh

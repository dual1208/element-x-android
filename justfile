set shell := ["bash", "-euo", "pipefail", "-c"]

serial := env_var_or_default("ANDROID_SERIAL", "")
gradle := "JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew --no-daemon --max-workers=2 -Dorg.gradle.jvmargs='-Xmx3g -Dfile.encoding=UTF-8 -XX:+UseG1GC'"
logs := "build/codex-logs"
apk := "app/build/outputs/apk/gplay/debug/app-gplay-arm64-v8a-debug.apk"
package := "io.element.android.x.debug"

device:
    test -n "{{serial}}" || { echo "ANDROID_SERIAL is required for physical-device commands" >&2; exit 2; }
    test "$(adb -s "{{serial}}" get-state)" = "device"
    test "$(adb -s "{{serial}}" shell getprop ro.kernel.qemu | tr -d '\r')" != "1"
    adb -s "{{serial}}" shell getprop ro.product.model
    adb -s "{{serial}}" shell getprop ro.build.version.sdk

fmt:
    {{gradle}} ktlintFormat

lint:
    mkdir -p {{logs}}
    {{gradle}} ktlintCheck 2>&1 | tee {{logs}}/lint.log

test:
    mkdir -p {{logs}}
    {{gradle}} :features:login:impl:testDebugUnitTest 2>&1 | tee {{logs}}/test-login.log

build:
    mkdir -p {{logs}}
    {{gradle}} :app:assembleGplayDebug 2>&1 | tee {{logs}}/build-gplay-debug.log

check: lint test

ci: check build

install: device build
    test -f {{apk}}
    adb -s "{{serial}}" install -r -d {{apk}} 2>&1 | tee {{logs}}/install.log

launch: device
    adb -s "{{serial}}" shell am force-stop {{package}}
    adb -s "{{serial}}" shell am start -W -n {{package}}/io.element.android.x.MainActivity 2>&1 | tee {{logs}}/launch.log

deploy: install launch

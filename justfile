set shell := ["bash", "-euo", "pipefail", "-c"]

serial := env_var_or_default("ANDROID_SERIAL", "")
gradle := "JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew --no-daemon --max-workers=1 -Dorg.gradle.jvmargs='-Xmx3g -Dfile.encoding=UTF-8 -XX:+UseG1GC'"
logs := "build/codex-logs"
apk := "app/build/intermediates/apk/gplay/debug/app-gplay-arm64-v8a-debug.apk"
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
    {{gradle}} :appnav:testDebugUnitTest :features:enterprise:impl-foss:testDebugUnitTest :features:ftue:impl:testDebugUnitTest :features:home:impl:testDebugUnitTest :features:invite:impl:testDebugUnitTest :features:login:impl:testDebugUnitTest :features:securebackup:impl:testDebugUnitTest 2>&1 | tee {{logs}}/test-managed-family.log

build:
    mkdir -p {{logs}}
    {{gradle}} -Pandroid.injected.build.abi=arm64-v8a -Pandroid.buildOnlyTargetAbi=true -Pandroid.injected.testOnly=false :app:assembleGplayDebug 2>&1 | tee {{logs}}/build-gplay-debug.log

check: lint test

ci: build

stage:
    #!/usr/bin/env bash
    set -euo pipefail
    export LC_ALL=C
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
    build_tools=/opt/homebrew/share/android-commandlinetools/build-tools/36.0.0
    stage_dir=build/codex-artifacts/family4
    test -f "{{apk}}"
    "$build_tools/apksigner" verify --verbose "{{apk}}"
    "$build_tools/zipalign" -c -P 16 -v 4 "{{apk}}" >/dev/null
    manifest="$($build_tools/aapt dump badging "{{apk}}")"
    xmltree="$($build_tools/aapt dump xmltree "{{apk}}" AndroidManifest.xml)"
    if grep -Eq 'A: android:testOnly.*0xffffffff' <<<"$xmltree"; then
        echo "Refusing to stage a testOnly APK" >&2
        exit 1
    fi
    application_id="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<<"$manifest")"
    version_code="$(sed -n "s/^package: .*versionCode='\([^']*\)'.*/\1/p" <<<"$manifest")"
    version_name="$(sed -n "s/^package: .*versionName='\([^']*\)'.*/\1/p" <<<"$manifest")"
    min_sdk="$(sed -n "s/^sdkVersion:'\([^']*\)'.*/\1/p" <<<"$manifest")"
    test -n "$application_id" && test -n "$version_code" && test -n "$version_name" && test -n "$min_sdk"

    umask 077
    certificate_dir="$(mktemp -d)"
    trap 'rm -rf "$certificate_dir"' EXIT
    packaged_identity="$certificate_dir/family-client.p12"
    leaf_certificate="$certificate_dir/leaf.pem"
    if ! unzip -p "{{apk}}" assets/family-client.p12 > "$packaged_identity" || [[ ! -s "$packaged_identity" ]]; then
        echo "Refusing to stage an APK without assets/family-client.p12" >&2
        exit 1
    fi
    if ! openssl pkcs12 -in "$packaged_identity" -clcerts -nokeys -passin pass: -out "$leaf_certificate"; then
        echo "Refusing to stage an APK with an unreadable family client identity" >&2
        exit 1
    fi
    certificate_count="$(grep -c '^-----BEGIN CERTIFICATE-----$' "$leaf_certificate" || true)"
    if [[ "$certificate_count" != "1" ]]; then
        echo "Refusing to stage an APK whose family identity does not contain exactly one leaf certificate" >&2
        exit 1
    fi
    expected_cn="android-${version_name}-${version_code}"
    certificate_cn="$(openssl x509 -in "$leaf_certificate" -noout -subject -nameopt RFC2253 | sed 's/^subject=//' | tr ',' '\n' | sed -n 's/^CN=//p')"
    if [[ "$certificate_cn" != "$expected_cn" ]]; then
        echo "Refusing to stage a family client certificate for a different release" >&2
        exit 1
    fi
    if ! openssl verify -purpose sslclient -partial_chain -trusted "$leaf_certificate" "$leaf_certificate" >/dev/null; then
        echo "Refusing to stage a family client certificate that is not currently valid for client authentication" >&2
        exit 1
    fi
    if ! openssl x509 -in "$leaf_certificate" -noout -ext extendedKeyUsage | grep -Eq 'TLS Web Client Authentication|1\.3\.6\.1\.5\.5\.7\.3\.2'; then
        echo "Refusing to stage a family client certificate without the clientAuth EKU" >&2
        exit 1
    fi

    rm -rf "$stage_dir"
    mkdir -p "$stage_dir"
    cp "{{apk}}" "$stage_dir/app-gplay-arm64-v8a-debug.apk"
    jq -n \
      --arg applicationId "$application_id" \
      --arg versionName "$version_name" \
      --arg outputFile "app-gplay-arm64-v8a-debug.apk" \
      --argjson versionCode "$version_code" \
      --argjson minSdk "$min_sdk" \
      '{version:3, artifactType:{type:"APK",kind:"Directory"}, applicationId:$applicationId, variantName:"gplayDebug", elements:[{type:"ONE_OF_MANY",filters:[{filterType:"ABI",value:"arm64-v8a"}],attributes:[],versionCode:$versionCode,versionName:$versionName,outputFile:$outputFile}],elementType:"File",minSdkVersionForDexing:$minSdk}' \
      > "$stage_dir/output-metadata.json"

install-built: device
    test -f {{apk}}
    adb -s "{{serial}}" install -r {{apk}} 2>&1 | tee {{logs}}/install.log

install: build install-built

launch: device
    adb -s "{{serial}}" shell am force-stop {{package}}
    adb -s "{{serial}}" shell am start -W -n {{package}}/io.element.android.x.MainActivity 2>&1 | tee {{logs}}/launch.log

deploy: install launch

# Build Environment Setup

## Building in the Claude Code sandbox (one-time setup)

The Claude Code VM does not ship with the Android SDK — you must provision it yourself before running Gradle, and do not rely on GitHub Actions for verification. Its preinstalled JDK is version 21, matching the `jvmToolchain(21)` used across modules; run `java -version` first, and skip step 1 below if it already reports 21. Prefer `plainDebug` since it needs no proprietary overlay or signing secrets.

Install paths used below (pick any, but keep them consistent):

- JDK 21 (only if not already preinstalled): `/home/user/tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64`
- Android SDK: `/home/user/android-sdk`

**Disk footprint:** expect ~5 GB across all of these combined (less if the preinstalled JDK 21 is used directly) — NDK r28c alone is ~2 GB unpacked, the rest of the SDK is ~1 GB, and `~/.gradle` grows to ~2 GB after the first build. Check free space before starting.

**Environment.** Every step below needs the same env vars. Write them once and source them each time — point `JAVA_HOME` at the preinstalled JDK (`dirname $(dirname $(readlink -f $(which java)))`) unless you installed Zulu JDK 21 yourself in step 1, in which case use that path instead:

```bash
cat > /home/user/tools/android-env.sh <<'EOF'
export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(which java)")")")"
export ANDROID_HOME=/home/user/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
unset JAVA_TOOL_OPTIONS  # strip the sandbox's -Dhttp.proxyHost that would poison Gradle downloads
EOF
```

Then `source /home/user/tools/android-env.sh` at the start of any shell that runs `sdkmanager` or Gradle.

1. **Install Zulu JDK 21** — only needed if `java -version` did not already report 21 (e.g. the sandbox image changed):

   ```bash
   mkdir -p /home/user/tools && cd /home/user/tools
   curl -fsSL -o zulu21.tar.gz \
     https://cdn.azul.com/zulu/bin/zulu21.52.203-ca-jdk21.0.12.1-linux_x64.tar.gz
   echo "068872f682e4157896649c242a13660b33ece808f395095a00fbbd3de8b4bbca  zulu21.tar.gz" | sha256sum -c -
   tar xzf zulu21.tar.gz && rm zulu21.tar.gz
   ```

   Then edit `android-env.sh` to point `JAVA_HOME` at `/home/user/tools/zulu21.52.203-ca-jdk21.0.12.1-linux_x64` instead of the `which java` lookup, and re-source it.

2. **Trust the sandbox egress CA in the JDK truststore.** Outbound HTTPS in the Claude Code sandbox goes through an Anthropic TLS-inspection proxy (`sandbox-egress-production TLS Inspection CA`). `curl` trusts it via `/etc/ssl/certs`, but the JDK keeps its own `cacerts`, so `sdkmanager` and Gradle will fail with `PKIX path building failed` until you import the system CAs. This applies to the preinstalled JDK too, not just a freshly downloaded one:

   ```bash
   source /home/user/tools/android-env.sh
   for crt in /usr/local/share/ca-certificates/*.crt; do
     "$JAVA_HOME/bin/keytool" -importcert -noprompt -trustcacerts \
       -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit \
       -alias "$(basename "$crt" .crt)" -file "$crt"
   done
   ```

3. **Install the Android SDK command-line tools**, then use `sdkmanager` to fetch the exact packages Gradle expects (`compileSdk 36`, `build-tools 36.0.0`, `ndk 28.2.13676358` — the NDK is required because the `Snappy` module has JNI C++; note that `sdkmanager` resolves that NDK coordinate to `android-ndk-r28c` on disk, which is expected):

   ```bash
   mkdir -p /home/user/android-sdk/cmdline-tools && cd /home/user/android-sdk/cmdline-tools
   curl -fsSL -o cmdline-tools.zip \
     https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
   echo "7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee  cmdline-tools.zip" | sha256sum -c -
   unzip -q cmdline-tools.zip && mv cmdline-tools latest && rm cmdline-tools.zip

   source /home/user/tools/android-env.sh
   yes | sdkmanager --licenses > /dev/null  # silent; prints thousands of "y\n" lines otherwise
   sdkmanager "platform-tools" "platforms;android-36" \
              "build-tools;36.0.0" "ndk;28.2.13676358"
   ```

4. **Write `local.properties`** so Gradle skips its own SDK discovery:

   ```bash
   echo "sdk.dir=/home/user/android-sdk" > local.properties
   ```

5. **Build.**

   ```bash
   source /home/user/tools/android-env.sh
   ./gradlew assemblePlainDebug
   ```

   Expect ~3 minutes cold (Gradle downloads its pinned wrapper distribution and dependencies into `~/.gradle`). The APK lands at `Alkitab/build/outputs/apk/plain/debug/Alkitab-<code>-<version>-<hash>-yuku.alkitab.debug-dev.apk`.

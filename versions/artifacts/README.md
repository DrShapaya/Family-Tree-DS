# Build Artifacts

Build copies are grouped without mixing them with source files:

```text
android/apk/reference-1.x/   historical 1.x debug APKs
android/apk/native-2.x/      native 2.x debug and release APKs
android/aab/native-2.x/      native 2.x release bundles
```

Gradle-generated files remain inside each application's `build/` directory.
Only named copies intended for testing or handoff belong here.

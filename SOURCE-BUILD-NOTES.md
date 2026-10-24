# MOONDROP LE Control 0.7 source package

Contents: module Java source, Android manifest, Xposed entrypoint, resources, README, summary, release notes, and local build script. Signing keystores, passwords, generated build output, and APK files are intentionally excluded.

The included build.ps1 currently references the author's local Android SDK/JDK and release signing files under D:\Code. Adjust those paths for your environment. The Xposed API 82 compile-time JAR is included at the package root when available; it is a build dependency, not module source.

Application ID: io.github.oliverarcher.moondrop.lecontrol
Version: 0.7 (versionCode 7)

package com.sticklab.app

// Was das Hilfsprogramm dieser Version kennzeichnet: die echte App (Debug-Fassung siehe src/debug). Das Programm liegt in
// src/release/jniLibs, gebaut mit -DSTICKLAB_PACKAGE=\"com.sticklab.app\" (NDK r30 -O2 -Wall -Wextra), zweimal bitgleich
// 06.11.2026, 26912 Bytes, Name "StickLab Controller". Prüfsumme nie von Hand ändern, ohne das Programm neu zu bauen und zu prüfen
internal object MirrorBuild {
    const val PACKAGE = "com.sticklab.app"
    const val PROGRAM_SHA256 = "c0a456f0e15daaa0ac4b639f12777e739e19288af04f179491454401781f93b5"
}

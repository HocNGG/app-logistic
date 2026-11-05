plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    // Wave C — Android target. Khai ở ROOT với `apply false` để cả build dùng CHUNG một bản AGP
    // (nếu mỗi module tự khai version riêng, Gradle sẽ báo "plugin version conflict").
    alias(libs.plugins.androidApplication) apply false
}

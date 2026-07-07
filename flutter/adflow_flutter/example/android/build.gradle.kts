// Resolve 1 lần trong context của chính project root này ("example/android") - không được resolve
// bên trong closure allprojects{} bằng chuỗi tương đối, vì Gradle áp closure đó cho TỪNG project
// (root, :app, :adflow_flutter) và uri("...") tương đối sẽ bị resolve theo projectDir của project
// đang chạy closure tại thời điểm đó (vd :app), không phải theo root - dẫn tới sai đường dẫn.
val adflowLocalMavenDir = rootDir.resolve("../../android/local-maven")

allprojects {
    repositories {
        google()
        mavenCentral()
        // Local Maven repo tĩnh chứa :adflow-core/:adflow-admob (xem android/build.gradle.kts của
        // plugin). Gradle chỉ dùng repositories khai báo ở project SỞ HỮU configuration đang resolve
        // (ở đây là root/allprojects của chính app này) - khai báo riêng trong build.gradle.kts của
        // plugin không đủ để :app thấy được. Bất kỳ app Flutter thật nào dùng plugin adflow_flutter
        // cũng phải tự thêm dòng maven{} này vào android/build.gradle.kts của chính họ (xem README).
        maven { url = uri(adflowLocalMavenDir) }
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}

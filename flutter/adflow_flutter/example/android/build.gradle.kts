allprojects {
    repositories {
        google()
        mavenCentral()
        // adflow-core/adflow-admob được publish qua JitPack. Gradle chỉ dùng repositories khai báo
        // ở project SỞ HỮU configuration đang resolve (ở đây là root/allprojects của chính app này)
        // - khai báo riêng trong build.gradle.kts của plugin không đủ để :app thấy được. Bất kỳ app
        // Flutter thật nào dùng plugin adflow_flutter cũng phải tự thêm dòng maven() này vào
        // android/build.gradle.kts của chính họ (xem README).
        maven("https://jitpack.io")
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

plugins {
    id("kotlin")
    id("org.jetbrains.kotlin.jvm")
}

//apply("${rootProject.projectDir.path}/gradle/global_config.gradle.kts")
apply("${project.projectDir.path}/maven.gradle.kts")

// 将assemble生成的jar文件 重命名为 "插件名称-版本名称.jar"
tasks.register("renameOutputJar") {
    doLast {
        // 获取模块名
        val moduleName = project.name
        // 构造原始 JAR 文件的路径
        val originalJar = file("$buildDir/libs/${moduleName}.jar")
        // 构造目标文件的路径
        val renamedJar =
            file("$buildDir/libs/${properties["plugin_name"]}-${properties["plugin_version"]}.jar")
        // 检查 JAR 文件是否存在
        if (originalJar.exists()) {
            originalJar.renameTo(renamedJar) // 重命名文件
            println("Renamed ${originalJar.name} to ${renamedJar.name}")
        } else {
            println("JAR file not found: ${originalJar.absolutePath}")
        }
    }
}

// 确保在 `assemble` 任务完成后执行 `renameOutputJar`
tasks.named("assemble") {
    finalizedBy("renameOutputJar")
}

dependencies {
    compileOnly(gradleApi())
    api("org.codehaus.groovy:groovy-xml:3.0.13")
    compileOnly("org.ow2.asm:asm:9.3")
    compileOnly("com.android.tools.build:gradle:8.0.2")
    compileOnly("com.bytedance.android:aabresguard-plugin:0.1.10")
    compileOnly("com.tencent.mm:AndResGuard-gradle-plugin:1.2.21")
//    // // AabResGuard
//    compileOnly("com.bytedance.android:aabresguard-plugin:0.1.8")
//    // // AndResGuard - lalaki
//    compileOnly("cn.lalaki.AndResGuard:cn.lalaki.AndResGuard.gradle.plugin:1.5.1-final")

    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(properties["jdk_version"].toString().toInt()))
    }
}
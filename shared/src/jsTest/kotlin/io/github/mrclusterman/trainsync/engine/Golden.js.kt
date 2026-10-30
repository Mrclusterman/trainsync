package io.github.mrclusterman.trainsync.engine

private val fs: dynamic = js("require('fs')")

// El directorio de los casos lo pasa Gradle (shared/build.gradle.kts): Node arranca los tests desde build/js
internal actual fun readGolden(name: String): String =
    fs.readFileSync("${js("process.env.GOLDEN_DIR")}/$name.json", "utf8") as String

internal actual val updatingGolden = false

internal actual fun editGolden(name: String, edit: (String) -> String) { error("Los casos se regeneran en la JVM") }

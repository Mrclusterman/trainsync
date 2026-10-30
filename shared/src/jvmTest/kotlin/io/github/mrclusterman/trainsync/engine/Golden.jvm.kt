package io.github.mrclusterman.trainsync.engine

import java.io.File

internal actual fun readGolden(name: String): String =
    object {}::class.java.getResource("/golden/$name.json")!!.readText()

internal actual val updatingGolden = System.getenv("UPDATE_GOLDEN") == "1"

internal actual fun editGolden(name: String, edit: (String) -> String) {
    val file = File("src/jvmAndJsTest/resources/golden/$name.json")
    file.writeText(edit(file.readText()))
}

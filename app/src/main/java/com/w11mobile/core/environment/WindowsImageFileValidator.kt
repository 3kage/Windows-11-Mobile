package com.w11mobile.core.environment

object WindowsImageFileValidator {

    fun isSupportedFileName(fileName: String): Boolean =
        fileName.endsWith(".qcow2", ignoreCase = true) ||
            fileName.endsWith(".iso", ignoreCase = true)

    fun validateFileName(fileName: String): String? {
        val trimmed = fileName.trim()
        if (trimmed.isEmpty()) {
            return "Не вдалося визначити ім'я файлу."
        }
        if (isSupportedFileName(trimmed)) {
            return null
        }
        return when {
            trimmed.endsWith(".apk", ignoreCase = true) ->
                "Файл .apk — це програма Android, а не образ Windows. " +
                    "Оберіть файл Windows (.iso або .qcow2), наприклад Win11_*_Arm64.iso."
            trimmed.endsWith(".zip", ignoreCase = true) ||
                trimmed.endsWith(".rar", ignoreCase = true) ||
                trimmed.endsWith(".7z", ignoreCase = true) ->
                "Архів не підходить як образ диска. Розпакуйте або оберіть готовий .iso / .qcow2."
            else ->
                "Підтримуються лише файли .qcow2 або .iso (отримано: $trimmed)"
        }
    }
}

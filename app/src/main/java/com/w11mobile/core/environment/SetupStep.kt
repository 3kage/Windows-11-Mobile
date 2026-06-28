package com.w11mobile.core.environment

enum class SetupStep(val labelUk: String, val weight: Int) {
    IDLE("Очікування", 0),
    VERIFY_DEVICE("Перевірка пристрою", 5),
    INSTALL_PROOT("Встановлення PRoot", 10),
    INSTALL_ROOTFS("Завантаження Linux rootfs", 15),
    CONFIGURE_ROOTFS("Налаштування Termux-середовища", 10),
    INSTALL_QEMU("Встановлення QEMU", 25),
    DOWNLOAD_WINDOWS_IMAGE("Завантаження образу Windows", 30),
    VERIFY_ENVIRONMENT("Перевірка середовища", 5),
    COMPLETE("Готово", 0),
    ERROR("Помилка", 0),
    ;

    companion object {
        val pipelineSteps = listOf(
            VERIFY_DEVICE,
            INSTALL_PROOT,
            INSTALL_ROOTFS,
            CONFIGURE_ROOTFS,
            INSTALL_QEMU,
            DOWNLOAD_WINDOWS_IMAGE,
            VERIFY_ENVIRONMENT,
            COMPLETE,
        )

        fun progressBefore(step: SetupStep): Int =
            pipelineSteps.takeWhile { it != step }.sumOf { it.weight }
    }
}

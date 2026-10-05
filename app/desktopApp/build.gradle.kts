import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "org.fikohc.espkbd.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "EspKbd"
            packageVersion = "1.0.0"
            description = "Control an ESP32 BLE-to-USB keyboard bridge"
            // JNA (used by the BLE library) needs sun.misc.Unsafe
            modules("jdk.unsupported")

            macOS {
                bundleID = "org.fikohc.espkbd"
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSBluetoothAlwaysUsageDescription</key>
                        <string>EspKbd uses Bluetooth to send keystrokes to the ESP32 keyboard.</string>
                    """.trimIndent()
                }
            }
            windows {
                menuGroup = "EspKbd"
                upgradeUuid = "6f1b0f5e-3c2a-4b8e-9d7a-2e4c5b1a9f30"
            }
            linux {
                packageName = "espkbd"
            }
        }
    }
}
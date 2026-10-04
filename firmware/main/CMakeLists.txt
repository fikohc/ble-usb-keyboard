idf_component_register(SRCS "main.c" "keymap.c" "kbd.c" "usb_kbd.c" "ble_svc.c"
                    INCLUDE_DIRS "."
                    REQUIRES nvs_flash bt esp_timer)

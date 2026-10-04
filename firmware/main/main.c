#include "nvs_flash.h"
#include "ble_svc.h"
#include "kbd.h"
#include "usb_kbd.h"

void app_main(void)
{
    // Initialize NVS Flash (required for BLE)
    esp_err_t ret = nvs_flash_init();
    if (ret == ESP_ERR_NVS_NO_FREE_PAGES || ret == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        ESP_ERROR_CHECK(nvs_flash_erase());
        ret = nvs_flash_init();
    }
    ESP_ERROR_CHECK(ret);

    kbd_init();
    ESP_ERROR_CHECK(usb_kbd_init());
    ble_svc_init();
}

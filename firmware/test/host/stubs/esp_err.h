#pragma once
#include <assert.h>

typedef int esp_err_t;
#define ESP_OK 0
#define ESP_ERROR_CHECK(x) do { esp_err_t err_ = (x); assert(err_ == ESP_OK); (void)err_; } while (0)

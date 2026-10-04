// Tests are single-threaded, the mutex is a no-op
#pragma once
#include "freertos/FreeRTOS.h"

typedef void *SemaphoreHandle_t;

#define xSemaphoreCreateMutex()      ((SemaphoreHandle_t)1)
#define xSemaphoreTake(sem, wait)    ((void)(sem), (void)(wait), pdTRUE)
#define xSemaphoreGive(sem)          ((void)(sem), pdTRUE)

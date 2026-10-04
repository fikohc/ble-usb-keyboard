// Tasks are not started; tests step the kbd queue with kbd_process_next()
#pragma once
#include "freertos/FreeRTOS.h"

typedef void (*TaskFunction_t)(void *);
typedef void *TaskHandle_t;

#define xTaskCreate(fn, name, stack, arg, prio, handle) \
    ((void)(fn), (void)(name), (void)(stack), (void)(arg), (void)(prio), (void)(handle), pdPASS)

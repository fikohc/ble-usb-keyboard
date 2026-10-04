#pragma once

// Start NimBLE with the keyboard control service and begin advertising
void ble_svc_init(void);

// Notify subscribers that the held keys changed
void ble_svc_notify_keypress(void);

// Notify subscribers that the USB connection state changed
void ble_svc_notify_state(void);

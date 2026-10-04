# Host unit tests for keymap + kbd (keypress / keys / heartbeat) logic.
# Needs ESP-IDF (for Unity) and managed_components (for TinyUSB headers, fetched by `idf.py build`).
#   make -C test/host test

IDF_PATH ?= $(HOME)/.espressif/v6.1/esp-idf
ROOT     := ../..
TINYUSB  := $(ROOT)/managed_components/espressif__tinyusb/src
UNITY    := $(IDF_PATH)/components/unity/unity/src

CFLAGS := -std=gnu11 -g -Wall -fsanitize=address,undefined \
          -Istubs -I$(ROOT)/main -I$(TINYUSB) -I$(UNITY)

SRCS := test_main.c test_keymap.c test_kbd.c fakes.c \
        $(ROOT)/main/keymap.c $(ROOT)/main/kbd.c $(UNITY)/unity.c

BUILD := build

$(BUILD)/run_tests: $(SRCS) $(wildcard *.h stubs/*.h stubs/*/*.h $(ROOT)/main/*.h)
	@mkdir -p $(BUILD)
	$(CC) $(CFLAGS) $(SRCS) -o $@

.PHONY: test clean
test: $(BUILD)/run_tests
	./$(BUILD)/run_tests

clean:
	rm -rf $(BUILD)

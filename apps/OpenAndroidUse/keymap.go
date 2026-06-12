package main

import (
	"fmt"
	"strings"
)

// Android key codes (android.view.KeyEvent KEYCODE_* values).
const (
	keycodeEnter     = 66
	keycodeTab       = 61
	keycodeSpace     = 62
	keycodeDel       = 67
	keycodeForward   = 112
	keycodeEscape    = 111
	keycodeDpadUp    = 19
	keycodeDpadDown  = 20
	keycodeDpadLeft  = 21
	keycodeDpadRight = 22
	keycodeMoveHome  = 122
	keycodeMoveEnd   = 123
	keycodePageUp    = 92
	keycodePageDown  = 93
	keycodeCtrlLeft  = 113
	keycodeShiftLeft = 59
	keycodeAltLeft   = 57
	keycodeMetaLeft  = 117
	keycodeBack      = 4
	keycodeAppHome   = 3
	keycodeMenu      = 82
	keycodeAppSwitch = 187
)

// namedKeycodes maps xdotool-style key names (the cross-platform `press_key`
// contract) plus a few Android-specific aliases to Android key codes.
var namedKeycodes = map[string]int{
	"return":    keycodeEnter,
	"enter":     keycodeEnter,
	"kp_enter":  keycodeEnter,
	"tab":       keycodeTab,
	"space":     keycodeSpace,
	"backspace": keycodeDel,
	"delete":    keycodeForward,
	"escape":    keycodeEscape,
	"esc":       keycodeEscape,
	"up":        keycodeDpadUp,
	"down":      keycodeDpadDown,
	"left":      keycodeDpadLeft,
	"right":     keycodeDpadRight,
	"home":      keycodeMoveHome,
	"end":       keycodeMoveEnd,
	"page_up":   keycodePageUp,
	"prior":     keycodePageUp,
	"page_down": keycodePageDown,
	"next":      keycodePageDown,

	// Android navigation keys (not part of xdotool, but essential on a phone).
	"back":         keycodeBack,
	"android_home": keycodeAppHome,
	"menu":         keycodeMenu,
	"app_switch":   keycodeAppSwitch,
}

var modifierKeycodes = map[string]int{
	"ctrl":    keycodeCtrlLeft,
	"control": keycodeCtrlLeft,
	"shift":   keycodeShiftLeft,
	"alt":     keycodeAltLeft,
	"super":   keycodeMetaLeft,
	"meta":    keycodeMetaLeft,
	"cmd":     keycodeMetaLeft,
}

// parseKeySpec converts an xdotool-style key spec ("Return", "ctrl+a",
// "F5", "KP_0") into an ordered list of Android key codes; a single code
// means a plain keyevent, multiple codes mean a key combination.
func parseKeySpec(spec string) ([]int, error) {
	parts := strings.Split(strings.TrimSpace(spec), "+")
	var codes []int
	for index, part := range parts {
		part = strings.TrimSpace(part)
		if part == "" {
			return nil, fmt.Errorf("Invalid key spec %q", spec)
		}
		isLast := index == len(parts)-1
		if !isLast {
			modifier, ok := modifierKeycodes[strings.ToLower(part)]
			if !ok {
				return nil, fmt.Errorf("Unknown modifier %q in key spec %q", part, spec)
			}
			codes = append(codes, modifier)
			continue
		}
		code, err := singleKeycode(part)
		if err != nil {
			return nil, err
		}
		codes = append(codes, code)
	}
	return codes, nil
}

func singleKeycode(key string) (int, error) {
	lowered := strings.ToLower(key)
	if code, ok := namedKeycodes[lowered]; ok {
		return code, nil
	}
	if code, ok := functionKeycode(lowered); ok {
		return code, nil
	}
	if code, ok := keypadKeycode(lowered); ok {
		return code, nil
	}
	if len(key) == 1 {
		if code, ok := characterKeycode(rune(lowered[0])); ok {
			return code, nil
		}
	}
	return 0, fmt.Errorf("Unsupported key %q for the Android runtime", key)
}

func functionKeycode(lowered string) (int, bool) {
	number, found := strings.CutPrefix(lowered, "f")
	if !found || number == "" {
		return 0, false
	}
	value := 0
	for _, r := range number {
		if r < '0' || r > '9' {
			return 0, false
		}
		value = value*10 + int(r-'0')
	}
	if value < 1 || value > 12 {
		return 0, false
	}
	// KEYCODE_F1 = 131
	return 131 + value - 1, true
}

func keypadKeycode(lowered string) (int, bool) {
	digit, found := strings.CutPrefix(lowered, "kp_")
	if !found || len(digit) != 1 || digit[0] < '0' || digit[0] > '9' {
		return 0, false
	}
	// KEYCODE_NUMPAD_0 = 144
	return 144 + int(digit[0]-'0'), true
}

func characterKeycode(r rune) (int, bool) {
	switch {
	case r >= 'a' && r <= 'z':
		// KEYCODE_A = 29
		return 29 + int(r-'a'), true
	case r >= '0' && r <= '9':
		// KEYCODE_0 = 7
		return 7 + int(r-'0'), true
	case r == ' ':
		return keycodeSpace, true
	}
	return 0, false
}

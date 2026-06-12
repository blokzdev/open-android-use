package main

import "testing"

func TestParseKeySpecNamedKeys(t *testing.T) {
	cases := []struct {
		spec string
		want int
	}{
		{"Return", keycodeEnter},
		{"Tab", keycodeTab},
		{"BackSpace", keycodeDel},
		{"Delete", keycodeForward},
		{"Escape", keycodeEscape},
		{"Up", keycodeDpadUp},
		{"Page_Up", keycodePageUp},
		{"Prior", keycodePageUp},
		{"Next", keycodePageDown},
		{"Home", keycodeMoveHome},
		{"Back", keycodeBack},
		{"Menu", keycodeMenu},
		{"a", 29},
		{"z", 54},
		{"0", 7},
		{"9", 16},
		{"F1", 131},
		{"F12", 142},
		{"KP_0", 144},
		{"KP_9", 153},
	}
	for _, testCase := range cases {
		codes, err := parseKeySpec(testCase.spec)
		if err != nil {
			t.Fatalf("parseKeySpec(%q): %v", testCase.spec, err)
		}
		if len(codes) != 1 || codes[0] != testCase.want {
			t.Fatalf("parseKeySpec(%q) = %v, want [%d]", testCase.spec, codes, testCase.want)
		}
	}
}

func TestParseKeySpecCombinations(t *testing.T) {
	codes, err := parseKeySpec("ctrl+a")
	if err != nil {
		t.Fatal(err)
	}
	if len(codes) != 2 || codes[0] != keycodeCtrlLeft || codes[1] != 29 {
		t.Fatalf("ctrl+a = %v", codes)
	}
	codes, err = parseKeySpec("super+shift+Tab")
	if err != nil {
		t.Fatal(err)
	}
	if len(codes) != 3 || codes[0] != keycodeMetaLeft || codes[1] != keycodeShiftLeft || codes[2] != keycodeTab {
		t.Fatalf("super+shift+Tab = %v", codes)
	}
}

func TestParseKeySpecErrors(t *testing.T) {
	for _, spec := range []string{"", "bogus+a", "NotAKey", "F13", "KP_x", "+"} {
		if _, err := parseKeySpec(spec); err == nil {
			t.Fatalf("parseKeySpec(%q) should fail", spec)
		}
	}
}

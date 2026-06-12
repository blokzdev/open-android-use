package main

import (
	"bytes"
	"fmt"
	"image"
	"image/color"
	"image/png"
	"strings"
	"testing"
	"time"
)

type fakeRunner struct {
	calls   [][]string
	handler func(args []string) ([]byte, error)
}

func (f *fakeRunner) output(args ...string) ([]byte, error) {
	f.calls = append(f.calls, args)
	return f.handler(args)
}

func (f *fakeRunner) commandLines() []string {
	var lines []string
	for _, call := range f.calls {
		lines = append(lines, strings.Join(call, " "))
	}
	return lines
}

const sampleHierarchyXML = `<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>
<hierarchy rotation="0">
  <node index="0" text="" resource-id="" class="android.widget.FrameLayout" package="com.example.app" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[0,0][1080,2400]">
    <node index="0" text="Send" resource-id="com.example.app:id/send" class="android.widget.Button" package="com.example.app" content-desc="" checkable="false" checked="false" clickable="true" enabled="true" focusable="true" focused="false" scrollable="false" long-clickable="true" password="false" selected="false" bounds="[100,200][300,300]"/>
    <node index="1" text="" resource-id="com.example.app:id/input" class="android.widget.EditText" package="com.example.app" content-desc="Message" checkable="false" checked="false" clickable="true" enabled="true" focusable="true" focused="true" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[0,2200][1080,2380]"/>
    <node index="2" text="" resource-id="com.example.app:id/list" class="android.widget.ListView" package="com.example.app" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="true" long-clickable="false" password="false" selected="false" bounds="[0,300][1080,2200]"/>
    <node index="3" text="Static label" resource-id="" class="android.widget.TextView" package="com.example.app" content-desc="" checkable="false" checked="false" clickable="false" enabled="true" focusable="false" focused="false" scrollable="false" long-clickable="false" password="false" selected="false" bounds="[0,100][1080,180]"/>
  </node>
</hierarchy>`

func encodeTestPNG(width, height int) []byte {
	img := image.NewRGBA(image.Rect(0, 0, width, height))
	for y := 0; y < height; y++ {
		for x := 0; x < width; x++ {
			img.Set(x, y, color.RGBA{R: uint8(x % 256), G: uint8(y % 256), B: 128, A: 255})
		}
	}
	var buffer bytes.Buffer
	if err := png.Encode(&buffer, img); err != nil {
		panic(err)
	}
	return buffer.Bytes()
}

func newTestBridge(handler func(args []string) ([]byte, error)) (*adbBridge, *fakeRunner) {
	runner := &fakeRunner{handler: handler}
	bridge := &adbBridge{runner: runner, serial: "emulator-5554", sleep: func(time.Duration) {}}
	return bridge, runner
}

func deviceHandler(t *testing.T) func(args []string) ([]byte, error) {
	screenshot := encodeTestPNG(108, 240)
	return func(args []string) ([]byte, error) {
		joined := strings.Join(args, " ")
		switch {
		case joined == "devices":
			return []byte("List of devices attached\nemulator-5554\tdevice\n"), nil
		case strings.Contains(joined, "dumpsys activity activities"):
			return []byte("  mResumedActivity: ActivityRecord{abc u0 com.example.app/.MainActivity t12}\n"), nil
		case strings.Contains(joined, "query-activities"):
			return []byte("com.example.app/.MainActivity\ncom.android.settings/.Settings\n"), nil
		case strings.Contains(joined, "pm list packages"):
			return []byte("package:com.example.app\npackage:com.android.settings\n"), nil
		case strings.Contains(joined, "uiautomator dump"):
			return []byte("UI hierchary dumped to: " + dumpDevicePath), nil
		case strings.Contains(joined, "cat "+dumpDevicePath):
			return []byte(sampleHierarchyXML), nil
		case strings.Contains(joined, "rm -f"):
			return []byte(""), nil
		case strings.Contains(joined, "pidof"):
			return []byte("1234\n"), nil
		case strings.Contains(joined, "screencap"):
			return screenshot, nil
		case strings.Contains(joined, "input"):
			return []byte(""), nil
		case strings.Contains(joined, "monkey"):
			return []byte("Events injected: 1"), nil
		default:
			return nil, fmt.Errorf("unexpected adb call: %s", joined)
		}
	}
}

func TestConnectedDevicesParsing(t *testing.T) {
	runner := &fakeRunner{handler: func(args []string) ([]byte, error) {
		return []byte("List of devices attached\nemulator-5554\tdevice\nABC123\toffline\n* daemon started successfully\n"), nil
	}}
	devices, err := connectedDevices(runner)
	if err != nil {
		t.Fatal(err)
	}
	if len(devices) != 1 || devices[0] != "emulator-5554" {
		t.Fatalf("devices = %v", devices)
	}
}

func TestParseBounds(t *testing.T) {
	got := parseBounds("[100,200][300,300]")
	if got == nil || got.X != 100 || got.Y != 200 || got.Width != 200 || got.Height != 100 {
		t.Fatalf("parseBounds = %+v", got)
	}
	if parseBounds("[10,10][10,40]") != nil {
		t.Fatal("zero-width bounds should be rejected")
	}
	if parseBounds("garbage") != nil {
		t.Fatal("garbage bounds should be rejected")
	}
}

func TestParseQueryActivities(t *testing.T) {
	output := "com.example.app/.MainActivity\ncom.android.settings/com.android.settings.Settings\ncom.example.app/.OtherActivity\n"
	packages := parseQueryActivities(output)
	if len(packages) != 2 || packages[0] != "com.android.settings" || packages[1] != "com.example.app" {
		t.Fatalf("packages = %v", packages)
	}
}

func TestMatchPackage(t *testing.T) {
	packages := []string{"com.android.settings", "com.example.app", "com.example.application.pro"}
	cases := []struct{ query, want string }{
		{"com.example.app", "com.example.app"},
		{"settings", "com.android.settings"},
		{"Settings", "com.android.settings"},
		{"app", "com.example.app"},
		{"example", "com.example.app"},
		{"nomatch", ""},
	}
	for _, testCase := range cases {
		if got := matchPackage(packages, testCase.query); got != testCase.want {
			t.Fatalf("matchPackage(%q) = %q, want %q", testCase.query, got, testCase.want)
		}
	}
}

func TestParseUIHierarchyIndexesActionableElements(t *testing.T) {
	tree, elements, focused, err := parseUIHierarchy(sampleHierarchyXML, "com.example.app", 1)
	if err != nil {
		t.Fatal(err)
	}
	if len(elements) != 3 {
		t.Fatalf("element count = %d, want 3: %v", len(elements), tree)
	}
	button := elements[0]
	if button.Index != 1 || button.Name != "Send" || button.ControlType != "Button" {
		t.Fatalf("button = %+v", button)
	}
	if !containsAction(button.Actions, "click") || !containsAction(button.Actions, "long-click") {
		t.Fatalf("button actions = %v", button.Actions)
	}
	input := elements[1]
	if input.Name != "Message" || !containsAction(input.Actions, "set_value") || !input.Focused {
		t.Fatalf("input = %+v", input)
	}
	list := elements[2]
	if !containsAction(list.Actions, "scroll") {
		t.Fatalf("list actions = %v", list.Actions)
	}
	if !strings.Contains(focused, "EditText") || !strings.Contains(focused, "element 2") {
		t.Fatalf("focused summary = %q", focused)
	}
	joined := strings.Join(tree, "\n")
	if !strings.Contains(joined, `- TextView "Static label"`) {
		t.Fatalf("static text missing from tree:\n%s", joined)
	}
}

func TestParseUIHierarchyAppliesScale(t *testing.T) {
	_, elements, _, err := parseUIHierarchy(sampleHierarchyXML, "com.example.app", 0.5)
	if err != nil {
		t.Fatal(err)
	}
	button := elements[0]
	if button.Frame.X != 50 || button.Frame.Y != 100 || button.Frame.Width != 100 || button.Frame.Height != 50 {
		t.Fatalf("scaled frame = %+v", button.Frame)
	}
}

func TestScrollSwipeDirections(t *testing.T) {
	elementFrame := frame{X: 0, Y: 300, Width: 1080, Height: 1900}
	fromX, fromY, toX, toY := scrollSwipe(elementFrame, 1, "down", 1)
	if fromX != toX || fromY <= toY {
		t.Fatalf("scroll down should swipe up: from=(%d,%d) to=(%d,%d)", fromX, fromY, toX, toY)
	}
	fromX, fromY, toX, toY = scrollSwipe(elementFrame, 1, "right", 1)
	if fromY != toY || fromX <= toX {
		t.Fatalf("scroll right should swipe left: from=(%d,%d) to=(%d,%d)", fromX, fromY, toX, toY)
	}
	_, fullFromY, _, fullToY := scrollSwipe(elementFrame, 1, "down", 1)
	_, halfFromY, _, halfToY := scrollSwipe(elementFrame, 1, "down", 0.5)
	if halfFromY-halfToY >= fullFromY-fullToY {
		t.Fatal("fractional pages should shorten the swipe")
	}
}

func TestScrollSwipeRespectsScale(t *testing.T) {
	elementFrame := frame{X: 0, Y: 150, Width: 540, Height: 950}
	fromX, fromY, _, _ := scrollSwipe(elementFrame, 0.5, "down", 1)
	if fromX != 540 {
		t.Fatalf("fromX = %d, want device px 540", fromX)
	}
	if fromY <= 950 {
		t.Fatalf("fromY = %d, expected device pixel space", fromY)
	}
}

func TestEscapeInputText(t *testing.T) {
	got, err := escapeInputText("hi there")
	if err != nil {
		t.Fatal(err)
	}
	if got != "hi%sthere" {
		t.Fatalf("escaped = %q", got)
	}
	got, err = escapeInputText(`a"b'c$d`)
	if err != nil {
		t.Fatal(err)
	}
	if got != `a\"b\'c\$d` {
		t.Fatalf("escaped = %q", got)
	}
	if _, err := escapeInputText("héllo"); err == nil {
		t.Fatal("expected non-ASCII rejection")
	}
	if _, err := escapeInputText("emoji 🚀"); err == nil {
		t.Fatal("expected emoji rejection")
	}
}

func TestForegroundAppParsing(t *testing.T) {
	bridge, _ := newTestBridge(deviceHandler(t))
	pkg, activity, err := bridge.foregroundApp()
	if err != nil {
		t.Fatal(err)
	}
	if pkg != "com.example.app" || activity != ".MainActivity" {
		t.Fatalf("foreground = %s/%s", pkg, activity)
	}
}

func TestAppStateCapturesSnapshot(t *testing.T) {
	bridge, _ := newTestBridge(deviceHandler(t))
	snapshot, err := bridge.appState("example")
	if err != nil {
		t.Fatal(err)
	}
	if snapshot.App.BundleIdentifier != "com.example.app" {
		t.Fatalf("package = %s", snapshot.App.BundleIdentifier)
	}
	if snapshot.App.PID != 1234 {
		t.Fatalf("pid = %d", snapshot.App.PID)
	}
	if snapshot.ScreenshotPNGBase64 == "" {
		t.Fatal("missing screenshot")
	}
	if snapshot.CoordinateScale != 1 {
		t.Fatalf("scale = %v, want 1 (screenshot under max dimension)", snapshot.CoordinateScale)
	}
	if len(snapshot.Elements) != 3 {
		t.Fatalf("element count = %d", len(snapshot.Elements))
	}
	if snapshot.WindowBounds == nil || snapshot.WindowBounds.Width != 108 || snapshot.WindowBounds.Height != 240 {
		t.Fatalf("window bounds = %+v", snapshot.WindowBounds)
	}
}

func TestClickActionSendsScaledInputTap(t *testing.T) {
	bridge, runner := newTestBridge(deviceHandler(t))
	snapshot, err := bridge.action(actionRequest{
		Tool:        "click",
		App:         "example",
		Package:     "com.example.app",
		Element:     &elementRecord{Index: 1, Frame: &frame{X: 50, Y: 100, Width: 100, Height: 50}},
		Scale:       0.5,
		ClickCount:  1,
		MouseButton: "left",
	})
	if err != nil {
		t.Fatal(err)
	}
	if snapshot == nil {
		t.Fatal("expected post-action snapshot")
	}
	want := "-s emulator-5554 shell input tap 200 250"
	found := false
	for _, line := range runner.commandLines() {
		if line == want {
			found = true
		}
	}
	if !found {
		t.Fatalf("missing %q in:\n%s", want, strings.Join(runner.commandLines(), "\n"))
	}
}

func TestLongPressUsesSwipeHold(t *testing.T) {
	bridge, runner := newTestBridge(deviceHandler(t))
	_, err := bridge.action(actionRequest{
		Tool:        "click",
		App:         "example",
		Package:     "com.example.app",
		X:           floatPointer(100),
		Y:           floatPointer(200),
		Scale:       1,
		MouseButton: "right",
	})
	if err != nil {
		t.Fatal(err)
	}
	want := "-s emulator-5554 shell input swipe 100 200 100 200 600"
	if !containsLine(runner.commandLines(), want) {
		t.Fatalf("missing %q in:\n%s", want, strings.Join(runner.commandLines(), "\n"))
	}
}

func TestSecondaryActionRejectsUnsupportedAction(t *testing.T) {
	bridge, _ := newTestBridge(deviceHandler(t))
	_, err := bridge.action(actionRequest{
		Tool:    "perform_secondary_action",
		App:     "example",
		Package: "com.example.app",
		Element: &elementRecord{Index: 1, Frame: &frame{X: 0, Y: 0, Width: 10, Height: 10}, Actions: []string{"click"}},
		Action:  "long-click",
		Scale:   1,
	})
	if err == nil || !strings.Contains(err.Error(), "does not expose a long-click") {
		t.Fatalf("err = %v", err)
	}
}

func TestSetValueRequiresEditableElement(t *testing.T) {
	bridge, _ := newTestBridge(deviceHandler(t))
	_, err := bridge.action(actionRequest{
		Tool:    "set_value",
		App:     "example",
		Package: "com.example.app",
		Element: &elementRecord{Index: 1, Frame: &frame{X: 0, Y: 0, Width: 10, Height: 10}, Actions: []string{"click"}},
		Value:   "hello",
		Scale:   1,
	})
	if err == nil || !strings.Contains(err.Error(), "not a settable text element") {
		t.Fatalf("err = %v", err)
	}
}

func TestTypeTextRejectsNonASCIIBeforeTouchingDevice(t *testing.T) {
	bridge, runner := newTestBridge(deviceHandler(t))
	_, err := bridge.action(actionRequest{
		Tool:    "type_text",
		App:     "example",
		Package: "com.example.app",
		Text:    "héllo",
	})
	if err == nil {
		t.Fatal("expected error")
	}
	for _, line := range runner.commandLines() {
		if strings.Contains(line, "input text") {
			t.Fatalf("input text should not run: %s", line)
		}
	}
}

func TestUIAutomatorDumpRetriesOnce(t *testing.T) {
	attempts := 0
	base := deviceHandler(t)
	bridge, _ := newTestBridge(func(args []string) ([]byte, error) {
		joined := strings.Join(args, " ")
		if strings.Contains(joined, "cat "+dumpDevicePath) {
			attempts++
			if attempts == 1 {
				return []byte("not xml"), nil
			}
		}
		return base(args)
	})
	dump, err := bridge.uiautomatorDump()
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(dump, "<hierarchy") {
		t.Fatal("retry did not return hierarchy")
	}
	if attempts != 2 {
		t.Fatalf("attempts = %d, want 2", attempts)
	}
}

func containsLine(lines []string, want string) bool {
	for _, line := range lines {
		if line == want {
			return true
		}
	}
	return false
}

func floatPointer(value float64) *float64 {
	return &value
}

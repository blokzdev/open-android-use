package main

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
)

func startCompanionStub(t *testing.T, protocol int, setTextOK bool) (*httptest.Server, string) {
	t.Helper()
	var lastSetText string
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		switch request.URL.Path {
		case "/health":
			_ = json.NewEncoder(writer).Encode(map[string]any{
				"ok": true, "service": "open-android-use-companion",
				"version": "0.2.3", "protocol": protocol, "screenshot": true,
			})
		case "/action":
			body, _ := io.ReadAll(request.Body)
			var payload map[string]any
			_ = json.Unmarshal(body, &payload)
			if text, ok := payload["text"].(string); ok {
				lastSetText = text
				_ = lastSetText
			}
			if setTextOK {
				_ = json.NewEncoder(writer).Encode(map[string]any{"ok": true})
			} else {
				_ = json.NewEncoder(writer).Encode(map[string]any{"ok": false, "error": "no focused editable element"})
			}
		default:
			writer.WriteHeader(404)
		}
	}))
	t.Cleanup(server.Close)
	parsed, err := url.Parse(server.URL)
	if err != nil {
		t.Fatal(err)
	}
	return server, parsed.Port()
}

func companionTestBridge(t *testing.T, port string) (*adbBridge, *fakeRunner) {
	t.Helper()
	t.Setenv("OPEN_ANDROID_USE_COMPANION_PORT", port)
	bridge, runner := newTestBridge(deviceHandler(t))
	return bridge, runner
}

func TestCompanionConnectAcceptsMatchingProtocol(t *testing.T) {
	_, port := startCompanionStub(t, companionProtocolVersion, true)
	bridge, runner := companionTestBridge(t, port)
	health, err := bridge.companionLink().connect()
	if err != nil {
		t.Fatal(err)
	}
	if health.Version != "0.2.3" {
		t.Fatalf("version = %s", health.Version)
	}
	if !containsLine(runner.commandLines(), "-s emulator-5554 forward tcp:"+port+" tcp:"+port) {
		t.Fatalf("missing adb forward in:\n%s", strings.Join(runner.commandLines(), "\n"))
	}
}

func TestCompanionConnectRejectsProtocolMismatch(t *testing.T) {
	_, port := startCompanionStub(t, companionProtocolVersion+1, true)
	bridge, _ := companionTestBridge(t, port)
	if _, err := bridge.companionLink().connect(); err == nil || !strings.Contains(err.Error(), "protocol") {
		t.Fatalf("err = %v", err)
	}
}

func TestTypeTextUsesCompanionForUnicodeWhenEnabled(t *testing.T) {
	_, port := startCompanionStub(t, companionProtocolVersion, true)
	bridge, runner := companionTestBridge(t, port)
	t.Setenv("OPEN_ANDROID_USE_COMPANION", "1")
	if err := bridge.performTypeText("héllo 🚀 你好"); err != nil {
		t.Fatal(err)
	}
	for _, line := range runner.commandLines() {
		if strings.Contains(line, "input text") {
			t.Fatalf("ADB input text should not run when the companion handles it: %s", line)
		}
	}
}

func TestTypeTextSurfacesCompanionErrors(t *testing.T) {
	_, port := startCompanionStub(t, companionProtocolVersion, false)
	bridge, _ := companionTestBridge(t, port)
	t.Setenv("OPEN_ANDROID_USE_COMPANION", "1")
	err := bridge.performTypeText("héllo")
	if err == nil || !strings.Contains(err.Error(), "no focused editable element") {
		t.Fatalf("err = %v", err)
	}
}

func TestTypeTextFallsBackToADBForASCIIWhenCompanionUnreachable(t *testing.T) {
	server, port := startCompanionStub(t, companionProtocolVersion, true)
	server.Close()
	bridge, runner := companionTestBridge(t, port)
	t.Setenv("OPEN_ANDROID_USE_COMPANION", "1")
	if err := bridge.performTypeText("plain ascii"); err != nil {
		t.Fatal(err)
	}
	if !containsLine(runner.commandLines(), "-s emulator-5554 shell input text plain%sascii") {
		t.Fatalf("missing ADB fallback in:\n%s", strings.Join(runner.commandLines(), "\n"))
	}
}

func TestTypeTextNonASCIIWithUnreachableCompanionKeepsCompanionError(t *testing.T) {
	server, port := startCompanionStub(t, companionProtocolVersion, true)
	server.Close()
	bridge, _ := companionTestBridge(t, port)
	t.Setenv("OPEN_ANDROID_USE_COMPANION", "1")
	err := bridge.performTypeText("héllo")
	if err == nil || !strings.Contains(err.Error(), "not reachable") {
		t.Fatalf("err = %v", err)
	}
}

func TestTypeTextWithoutCompanionKeepsASCIIGuard(t *testing.T) {
	bridge, _ := newTestBridge(deviceHandler(t))
	err := bridge.performTypeText("héllo")
	if err == nil || !strings.Contains(err.Error(), "ASCII") {
		t.Fatalf("err = %v", err)
	}
}

const sampleCompanionTreeJSON = `{
  "className": "android.widget.FrameLayout",
  "bounds": [0, 0, 1080, 2400],
  "children": [
    {"className": "android.widget.Button", "text": "Send",
     "resourceId": "com.example.app:id/send",
     "bounds": [100, 200, 300, 300], "clickable": true, "longClickable": true},
    {"className": "android.widget.EditText", "contentDesc": "Message",
     "resourceId": "com.example.app:id/input",
     "bounds": [0, 2200, 1080, 2380], "clickable": true, "editable": true, "focused": true},
    {"className": "android.widget.ListView", "bounds": [0, 300, 1080, 2200], "scrollable": true},
    {"className": "android.widget.TextView", "text": "Static label", "bounds": [0, 100, 1080, 180]},
    {"className": "android.widget.Button", "text": "Disabled", "bounds": [0, 0, 100, 50],
     "clickable": true, "enabled": false}
  ]
}`

type companionStubState struct {
	actions []map[string]any
}

func startFullCompanionStub(t *testing.T, withScreenshot bool) (*companionStubState, string) {
	t.Helper()
	state := &companionStubState{}
	screenshot := encodeTestPNG(108, 240)
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		switch request.URL.Path {
		case "/health":
			_ = json.NewEncoder(writer).Encode(map[string]any{
				"ok": true, "service": "open-android-use-companion",
				"version": "0.2.3", "protocol": companionProtocolVersion, "screenshot": withScreenshot,
			})
		case "/snapshot":
			var tree map[string]any
			_ = json.Unmarshal([]byte(sampleCompanionTreeJSON), &tree)
			_ = json.NewEncoder(writer).Encode(map[string]any{
				"ok": true, "protocol": companionProtocolVersion,
				"package": "com.example.app", "tree": tree,
			})
		case "/screenshot":
			if !withScreenshot {
				writer.WriteHeader(http.StatusNotImplemented)
				_ = json.NewEncoder(writer).Encode(map[string]any{"ok": false, "error": "unsupported"})
				return
			}
			writer.Header().Set("Content-Type", "image/png")
			_, _ = writer.Write(screenshot)
		case "/action":
			body, _ := io.ReadAll(request.Body)
			var payload map[string]any
			_ = json.Unmarshal(body, &payload)
			state.actions = append(state.actions, payload)
			_ = json.NewEncoder(writer).Encode(map[string]any{"ok": true})
		default:
			writer.WriteHeader(404)
		}
	}))
	t.Cleanup(server.Close)
	parsed, err := url.Parse(server.URL)
	if err != nil {
		t.Fatal(err)
	}
	return state, parsed.Port()
}

func TestFlattenCompanionTreeMatchesUIAutomatorFormat(t *testing.T) {
	var tree companionNode
	if err := json.Unmarshal([]byte(sampleCompanionTreeJSON), &tree); err != nil {
		t.Fatal(err)
	}
	treeLines, elements, focused := flattenCompanionTree(&tree, 1)
	if len(elements) != 3 {
		t.Fatalf("element count = %d, want 3 (disabled button excluded):\n%s", len(elements), strings.Join(treeLines, "\n"))
	}
	if elements[0].Name != "Send" || !containsAction(elements[0].Actions, "long-click") {
		t.Fatalf("button = %+v", elements[0])
	}
	if elements[1].Name != "Message" || !containsAction(elements[1].Actions, "set_value") {
		t.Fatalf("input = %+v", elements[1])
	}
	if !strings.Contains(focused, "EditText") {
		t.Fatalf("focused = %q", focused)
	}
	want := `[1] Button "Send" (id: com.example.app:id/send) {{x: 100, y: 200, width: 200, height: 100}} [click long-click]`
	if !containsLine(trimAll(treeLines), want) {
		t.Fatalf("missing line %q in:\n%s", want, strings.Join(treeLines, "\n"))
	}
}

func trimAll(lines []string) []string {
	var trimmed []string
	for _, line := range lines {
		trimmed = append(trimmed, strings.TrimSpace(line))
	}
	return trimmed
}

func TestCompanionBackedSnapshotThroughBridge(t *testing.T) {
	_, port := startFullCompanionStub(t, true)
	bridge, runner := companionTestBridge(t, port)
	t.Setenv("OPEN_ANDROID_USE_COMPANION", "1")
	snapshot, err := bridge.appState("example")
	if err != nil {
		t.Fatal(err)
	}
	if snapshot.App.BundleIdentifier != "com.example.app" {
		t.Fatalf("package = %s", snapshot.App.BundleIdentifier)
	}
	if len(snapshot.Elements) != 3 || snapshot.ScreenshotPNGBase64 == "" {
		t.Fatalf("elements = %d, screenshot empty = %v", len(snapshot.Elements), snapshot.ScreenshotPNGBase64 == "")
	}
	for _, line := range runner.commandLines() {
		if strings.Contains(line, "uiautomator") {
			t.Fatalf("uiautomator should not run when the companion serves the snapshot: %s", line)
		}
	}
}

func TestCompanionSnapshotFallsBackToADBScreenshot(t *testing.T) {
	_, port := startFullCompanionStub(t, false)
	bridge, runner := companionTestBridge(t, port)
	t.Setenv("OPEN_ANDROID_USE_COMPANION", "1")
	snapshot, err := bridge.appState("example")
	if err != nil {
		t.Fatal(err)
	}
	if snapshot.ScreenshotPNGBase64 == "" {
		t.Fatal("expected adb screencap fallback to fill the screenshot")
	}
	if !containsLine(runner.commandLines(), "-s emulator-5554 exec-out screencap -p") {
		t.Fatalf("missing screencap fallback in:\n%s", strings.Join(runner.commandLines(), "\n"))
	}
}

func TestCompanionUnreachableFallsBackToUIAutomator(t *testing.T) {
	server, port := startCompanionStub(t, companionProtocolVersion, true)
	server.Close()
	bridge, _ := companionTestBridge(t, port)
	t.Setenv("OPEN_ANDROID_USE_COMPANION", "1")
	snapshot, err := bridge.appState("example")
	if err != nil {
		t.Fatal(err)
	}
	if len(snapshot.Elements) != 3 {
		t.Fatalf("uiautomator fallback elements = %d", len(snapshot.Elements))
	}
}

func TestClickRoutesThroughCompanionGesture(t *testing.T) {
	state, port := startFullCompanionStub(t, true)
	bridge, runner := companionTestBridge(t, port)
	t.Setenv("OPEN_ANDROID_USE_COMPANION", "1")
	_, err := bridge.action(actionRequest{
		Tool:        "click",
		App:         "example",
		Package:     "com.example.app",
		Element:     &elementRecord{Index: 1, Frame: &frame{X: 100, Y: 200, Width: 200, Height: 100}},
		Scale:       1,
		ClickCount:  1,
		MouseButton: "left",
	})
	if err != nil {
		t.Fatal(err)
	}
	var tap map[string]any
	for _, action := range state.actions {
		if action["type"] == "tap" {
			tap = action
		}
	}
	if tap == nil {
		t.Fatalf("companion did not receive a tap; actions = %v", state.actions)
	}
	if tap["x"].(float64) != 200 || tap["y"].(float64) != 250 {
		t.Fatalf("tap point = (%v, %v), want (200, 250)", tap["x"], tap["y"])
	}
	for _, line := range runner.commandLines() {
		if strings.Contains(line, "input tap") {
			t.Fatalf("ADB input tap should not run: %s", line)
		}
	}
}

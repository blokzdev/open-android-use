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

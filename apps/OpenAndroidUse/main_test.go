package main

import (
	"encoding/json"
	"strings"
	"testing"
)

type fakeBridge struct {
	snapshot *appSnapshot
	actions  []actionRequest
}

func (f *fakeBridge) listApps() (string, error) {
	return "Launchable apps:\n- com.example.app", nil
}

func (f *fakeBridge) appState(app string) (*appSnapshot, error) {
	return f.snapshot, nil
}

func (f *fakeBridge) action(request actionRequest) (*appSnapshot, error) {
	f.actions = append(f.actions, request)
	return f.snapshot, nil
}

func sampleSnapshot() *appSnapshot {
	return &appSnapshot{
		App:         appDescriptor{Name: "app", BundleIdentifier: "com.example.app", PID: 1234},
		WindowTitle: ".MainActivity",
		TreeLines:   []string{"[1] Button \"Send\" {{x: 50, y: 100, width: 100, height: 50}} [click]"},
		Elements: []elementRecord{
			{Index: 1, Name: "Send", ControlType: "Button", Frame: &frame{X: 50, Y: 100, Width: 100, Height: 50}, Actions: []string{"click"}},
		},
		CoordinateScale: 0.5,
	}
}

func TestToolDefinitionCount(t *testing.T) {
	if got := len(toolDefinitions()); got != 9 {
		t.Fatalf("toolDefinitions() count = %d, want 9", got)
	}
}

func TestToolDefinitionsMatchComputerUseSurface(t *testing.T) {
	want := map[string]bool{
		"list_apps": true, "get_app_state": true, "click": true,
		"perform_secondary_action": true, "scroll": true, "drag": true,
		"type_text": true, "press_key": true, "set_value": true,
	}
	for _, definition := range toolDefinitions() {
		if !want[definition.Name] {
			t.Fatalf("unexpected tool %q", definition.Name)
		}
		delete(want, definition.Name)
	}
	if len(want) != 0 {
		t.Fatalf("missing tools: %v", want)
	}
}

func TestActionToolsRequireSnapshotFirst(t *testing.T) {
	svc := newServiceWithBridge(&fakeBridge{snapshot: sampleSnapshot()})
	result := svc.callTool("click", map[string]any{"app": "example", "element_index": "1"})
	if !result.IsError {
		t.Fatal("expected error without prior get_app_state")
	}
	if !strings.Contains(result.Content[0].Text, "Run get_app_state") {
		t.Fatalf("unexpected error text: %s", result.Content[0].Text)
	}
}

func TestClickRequiresElementOrCoordinates(t *testing.T) {
	bridge := &fakeBridge{snapshot: sampleSnapshot()}
	svc := newServiceWithBridge(bridge)
	svc.callTool("get_app_state", map[string]any{"app": "example"})
	result := svc.callTool("click", map[string]any{"app": "example"})
	if !result.IsError {
		t.Fatal("expected error for click without target")
	}
}

func TestClickForwardsElementAndScaleToBridge(t *testing.T) {
	bridge := &fakeBridge{snapshot: sampleSnapshot()}
	svc := newServiceWithBridge(bridge)
	if result := svc.callTool("get_app_state", map[string]any{"app": "example"}); result.IsError {
		t.Fatalf("get_app_state failed: %s", result.Content[0].Text)
	}
	result := svc.callTool("click", map[string]any{"app": "example", "element_index": "1"})
	if result.IsError {
		t.Fatalf("click failed: %s", result.Content[0].Text)
	}
	if len(bridge.actions) != 1 {
		t.Fatalf("bridge action count = %d, want 1", len(bridge.actions))
	}
	request := bridge.actions[0]
	if request.Tool != "click" || request.Package != "com.example.app" {
		t.Fatalf("unexpected request: %+v", request)
	}
	if request.Element == nil || request.Element.Index != 1 {
		t.Fatalf("element not forwarded: %+v", request.Element)
	}
	if request.Scale != 0.5 {
		t.Fatalf("scale = %v, want 0.5", request.Scale)
	}
}

func TestSnapshotIsRememberedUnderPackageAndQuery(t *testing.T) {
	bridge := &fakeBridge{snapshot: sampleSnapshot()}
	svc := newServiceWithBridge(bridge)
	svc.callTool("get_app_state", map[string]any{"app": "Example"})
	for _, key := range []string{"example", "com.example.app", "1234"} {
		if svc.currentSnapshot(key) == nil {
			t.Fatalf("snapshot not remembered under %q", key)
		}
	}
}

func TestUnknownElementIndexFails(t *testing.T) {
	bridge := &fakeBridge{snapshot: sampleSnapshot()}
	svc := newServiceWithBridge(bridge)
	svc.callTool("get_app_state", map[string]any{"app": "example"})
	result := svc.callTool("click", map[string]any{"app": "example", "element_index": "99"})
	if !result.IsError || !strings.Contains(result.Content[0].Text, "unknown element_index") {
		t.Fatalf("unexpected result: %+v", result)
	}
}

func TestScrollValidatesDirectionAndPages(t *testing.T) {
	bridge := &fakeBridge{snapshot: sampleSnapshot()}
	svc := newServiceWithBridge(bridge)
	svc.callTool("get_app_state", map[string]any{"app": "example"})
	if result := svc.callTool("scroll", map[string]any{"app": "example", "element_index": "1", "direction": "sideways"}); !result.IsError {
		t.Fatal("expected invalid direction error")
	}
	if result := svc.callTool("scroll", map[string]any{"app": "example", "element_index": "1", "direction": "down", "pages": -1.0}); !result.IsError {
		t.Fatal("expected pages validation error")
	}
}

func TestCallSequenceStopsAfterFirstToolError(t *testing.T) {
	output, hasError, err := runCallCommand([]string{
		"--calls",
		`[{"tool":"not_a_tool"},{"tool":"list_apps"}]`,
	}, newServiceWithBridge(&fakeBridge{snapshot: sampleSnapshot()}))
	if err != nil {
		t.Fatal(err)
	}
	if !hasError {
		t.Fatal("expected hasError")
	}
	items, ok := output.([]map[string]any)
	if !ok {
		t.Fatalf("output type = %T", output)
	}
	if len(items) != 1 {
		t.Fatalf("sequence output count = %d, want 1", len(items))
	}
}

func TestReadArgumentsAcceptsJSONObject(t *testing.T) {
	args, err := readArguments(`{"app":"Settings","pages":2}`, "")
	if err != nil {
		t.Fatal(err)
	}
	if args["app"] != "Settings" {
		t.Fatalf("app = %v", args["app"])
	}
	if args["pages"].(json.Number).String() != "2" {
		t.Fatalf("pages = %v", args["pages"])
	}
}

func TestMCPInitializeResponse(t *testing.T) {
	request := map[string]any{
		"jsonrpc": "2.0",
		"id":      float64(1),
		"method":  "initialize",
		"params":  map[string]any{},
	}
	response := handleMCPRequest(request, newServiceWithBridge(&fakeBridge{}))
	result, ok := response["result"].(map[string]any)
	if !ok {
		t.Fatalf("missing result: %v", response)
	}
	serverInfo, _ := result["serverInfo"].(map[string]any)
	if serverInfo["name"] != "open-android-use" {
		t.Fatalf("server name = %v", serverInfo["name"])
	}
	if _, ok := result["capabilities"].(map[string]any)["tools"]; !ok {
		t.Fatal("missing tools capability")
	}
}

func TestMCPToolsListReturnsNineTools(t *testing.T) {
	response := handleMCPRequest(map[string]any{
		"jsonrpc": "2.0", "id": float64(2), "method": "tools/list",
	}, newServiceWithBridge(&fakeBridge{}))
	result := response["result"].(map[string]any)
	tools := result["tools"].([]toolDefinition)
	if len(tools) != 9 {
		t.Fatalf("tools/list count = %d, want 9", len(tools))
	}
}

func TestRenderedTextIncludesAppAndFocus(t *testing.T) {
	snapshot := sampleSnapshot()
	snapshot.FocusedSummary = `EditText "Message" (element 2)`
	text := snapshot.renderedText()
	if !strings.Contains(text, "App=com.example.app (pid 1234)") {
		t.Fatalf("missing app line: %s", text)
	}
	if !strings.Contains(text, `The focused UI element is EditText "Message" (element 2).`) {
		t.Fatalf("missing focus line: %s", text)
	}
}

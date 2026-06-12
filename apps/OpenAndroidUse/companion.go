package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"
)

// companionProtocolVersion is the wire protocol this bridge speaks; the
// on-device companion (apps/OpenAndroidUseCompanion) reports its version in
// /health and the bridge refuses mismatches. Spec:
// docs/design-docs/on-device-companion.md.
const companionProtocolVersion = 1

const defaultCompanionPort = 8355

type companionHealth struct {
	OK         bool   `json:"ok"`
	Service    string `json:"service"`
	Version    string `json:"version"`
	Protocol   int    `json:"protocol"`
	Screenshot bool   `json:"screenshot"`
}

type companionActionResponse struct {
	OK    bool   `json:"ok"`
	Error string `json:"error"`
}

// companionClient talks to the on-device companion through an adb-forwarded
// loopback port. It is created per bridge and probed lazily.
type companionClient struct {
	bridge *adbBridge
	port   int
	http   *http.Client
}

func newCompanionClient(bridge *adbBridge) *companionClient {
	return &companionClient{
		bridge: bridge,
		port:   companionPort(),
		http:   &http.Client{Timeout: 10 * time.Second},
	}
}

func companionPort() int {
	if value, err := strconv.Atoi(strings.TrimSpace(os.Getenv("OPEN_ANDROID_USE_COMPANION_PORT"))); err == nil && value > 0 && value < 65536 {
		return value
	}
	return defaultCompanionPort
}

func companionEnabled() bool {
	return strings.TrimSpace(os.Getenv("OPEN_ANDROID_USE_COMPANION")) == "1"
}

// connect ensures the adb port forward exists and the companion answers
// /health with a compatible protocol.
func (c *companionClient) connect() (*companionHealth, error) {
	if err := c.bridge.ensureSerial(); err != nil {
		return nil, err
	}
	forward := strconv.Itoa(c.port)
	if _, err := c.bridge.runner.output("-s", c.bridge.serial, "forward", "tcp:"+forward, "tcp:"+forward); err != nil {
		return nil, fmt.Errorf("Could not forward companion port %d: %s", c.port, err)
	}
	response, err := c.http.Get(c.url("/health"))
	if err != nil {
		return nil, fmt.Errorf("Companion is not reachable on 127.0.0.1:%d. Install the companion APK and enable its accessibility service.", c.port)
	}
	defer response.Body.Close()
	var health companionHealth
	if err := json.NewDecoder(response.Body).Decode(&health); err != nil || !health.OK {
		return nil, fmt.Errorf("Companion answered on port %d but the health payload is invalid.", c.port)
	}
	if health.Protocol != companionProtocolVersion {
		return nil, fmt.Errorf("Companion speaks protocol %d but this bridge requires %d. Update the companion app or the bridge.", health.Protocol, companionProtocolVersion)
	}
	return &health, nil
}

func (c *companionClient) url(path string) string {
	return fmt.Sprintf("http://127.0.0.1:%d%s", c.port, path)
}

// setText types arbitrary Unicode text into the focused editable element via
// the companion's ACTION_SET_TEXT path — the capability raw ADB lacks.
func (c *companionClient) setText(text string) error {
	if _, err := c.connect(); err != nil {
		return err
	}
	payload, err := json.Marshal(map[string]any{"type": "setText", "text": text})
	if err != nil {
		return err
	}
	response, err := c.http.Post(c.url("/action"), "application/json", bytes.NewReader(payload))
	if err != nil {
		return fmt.Errorf("Companion action failed: %s", err)
	}
	defer response.Body.Close()
	var result companionActionResponse
	if err := json.NewDecoder(response.Body).Decode(&result); err != nil {
		return fmt.Errorf("Companion returned an invalid action response: %s", err)
	}
	if !result.OK {
		return fmt.Errorf("Companion setText failed: %s", result.Error)
	}
	return nil
}

type companionNode struct {
	ClassName     string          `json:"className"`
	Text          string          `json:"text"`
	ContentDesc   string          `json:"contentDesc"`
	ResourceID    string          `json:"resourceId"`
	Bounds        []int           `json:"bounds"`
	Clickable     bool            `json:"clickable"`
	LongClickable bool            `json:"longClickable"`
	Scrollable    bool            `json:"scrollable"`
	Editable      bool            `json:"editable"`
	Focusable     bool            `json:"focusable"`
	Focused       bool            `json:"focused"`
	Checkable     bool            `json:"checkable"`
	Checked       bool            `json:"checked"`
	Selected      bool            `json:"selected"`
	Password      bool            `json:"password"`
	Enabled       *bool           `json:"enabled"`
	Children      []companionNode `json:"children"`
}

func (n companionNode) isEnabled() bool {
	return n.Enabled == nil || *n.Enabled
}

type companionSnapshot struct {
	OK       bool           `json:"ok"`
	Error    string         `json:"error"`
	Protocol int            `json:"protocol"`
	Package  string         `json:"package"`
	Tree     *companionNode `json:"tree"`
}

// snapshotTree fetches the live accessibility tree of the active window.
func (c *companionClient) snapshotTree() (*companionSnapshot, error) {
	if _, err := c.connect(); err != nil {
		return nil, err
	}
	response, err := c.http.Get(c.url("/snapshot"))
	if err != nil {
		return nil, fmt.Errorf("Companion snapshot failed: %s", err)
	}
	defer response.Body.Close()
	var snapshot companionSnapshot
	if err := json.NewDecoder(response.Body).Decode(&snapshot); err != nil {
		return nil, fmt.Errorf("Companion returned an invalid snapshot: %s", err)
	}
	if !snapshot.OK {
		return nil, fmt.Errorf("Companion snapshot failed: %s", snapshot.Error)
	}
	if snapshot.Tree == nil {
		return nil, fmt.Errorf("Companion snapshot is missing the tree.")
	}
	return &snapshot, nil
}

// screenshotPNG fetches a full-resolution screenshot. A (nil, nil) return means
// the companion cannot take screenshots on this Android version.
func (c *companionClient) screenshotPNG() ([]byte, error) {
	if _, err := c.connect(); err != nil {
		return nil, err
	}
	response, err := c.http.Get(c.url("/screenshot"))
	if err != nil {
		return nil, fmt.Errorf("Companion screenshot failed: %s", err)
	}
	defer response.Body.Close()
	if response.StatusCode == http.StatusNotImplemented {
		return nil, nil
	}
	if response.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("Companion screenshot failed with status %d", response.StatusCode)
	}
	var buffer bytes.Buffer
	if _, err := buffer.ReadFrom(response.Body); err != nil {
		return nil, err
	}
	return buffer.Bytes(), nil
}

// gesture posts a protocol-v1 action (tap, longPress, swipe, global).
func (c *companionClient) gesture(action map[string]any) error {
	if _, err := c.connect(); err != nil {
		return err
	}
	payload, err := json.Marshal(action)
	if err != nil {
		return err
	}
	response, err := c.http.Post(c.url("/action"), "application/json", bytes.NewReader(payload))
	if err != nil {
		return fmt.Errorf("Companion action failed: %s", err)
	}
	defer response.Body.Close()
	var result companionActionResponse
	if err := json.NewDecoder(response.Body).Decode(&result); err != nil {
		return fmt.Errorf("Companion returned an invalid action response: %s", err)
	}
	if !result.OK {
		return fmt.Errorf("Companion action failed: %s", result.Error)
	}
	return nil
}

// describe returns a one-line doctor status for the companion.
func (c *companionClient) describe() string {
	health, err := c.connect()
	if err != nil {
		return "companion: not available (" + err.Error() + ")"
	}
	mode := "available; set OPEN_ANDROID_USE_COMPANION=1 to use it"
	if companionEnabled() {
		mode = "enabled"
	}
	return fmt.Sprintf("companion: %s (v%s, protocol %d, via 127.0.0.1:%d)", mode, health.Version, health.Protocol, c.port)
}

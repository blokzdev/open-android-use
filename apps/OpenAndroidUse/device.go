package main

import (
	"context"
	"encoding/base64"
	"encoding/xml"
	"errors"
	"fmt"
	"math"
	"os"
	"os/exec"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"
)

const (
	adbTimeout        = 30 * time.Second
	launchWaitTimeout = 6 * time.Second
	actionSettleDelay = 800 * time.Millisecond
	maxElements       = 250
	maxTreeLines      = 600
	dumpDevicePath    = "/data/local/tmp/open-android-use-dump.xml"
)

type commandRunner interface {
	output(args ...string) ([]byte, error)
}

type execRunner struct {
	adbPath string
}

func (r execRunner) output(args ...string) ([]byte, error) {
	ctx, cancel := context.WithTimeout(context.Background(), adbTimeout)
	defer cancel()
	cmd := exec.CommandContext(ctx, r.adbPath, args...)
	output, err := cmd.CombinedOutput()
	if ctx.Err() == context.DeadlineExceeded {
		return nil, fmt.Errorf("adb %s timed out after %s", strings.Join(args, " "), adbTimeout)
	}
	if err != nil {
		text := strings.TrimSpace(string(output))
		if text == "" {
			text = err.Error()
		}
		return nil, fmt.Errorf("adb %s failed: %s", strings.Join(args, " "), text)
	}
	return output, nil
}

type adbBridge struct {
	runner    commandRunner
	serial    string
	sleep     func(time.Duration)
	companion *companionClient
}

func (b *adbBridge) companionLink() *companionClient {
	if b.companion == nil {
		b.companion = newCompanionClient(b)
	}
	return b.companion
}

func newADBBridge() *adbBridge {
	return &adbBridge{
		runner: execRunner{adbPath: adbPath()},
		sleep:  time.Sleep,
	}
}

func adbPath() string {
	if value := strings.TrimSpace(os.Getenv("OPEN_ANDROID_USE_ADB")); value != "" {
		return value
	}
	return "adb"
}

func (b *adbBridge) ensureSerial() error {
	if b.serial != "" {
		return nil
	}
	if value := strings.TrimSpace(os.Getenv("OPEN_ANDROID_USE_SERIAL")); value != "" {
		b.serial = value
		return nil
	}
	devices, err := connectedDevices(b.runner)
	if err != nil {
		return err
	}
	if len(devices) == 0 {
		return errors.New("No Android device is connected. Connect a device or emulator and verify with `adb devices`.")
	}
	if len(devices) > 1 {
		return fmt.Errorf("Multiple Android devices are connected (%s). Set OPEN_ANDROID_USE_SERIAL to choose one.", strings.Join(devices, ", "))
	}
	b.serial = devices[0]
	return nil
}

func connectedDevices(runner commandRunner) ([]string, error) {
	output, err := runner.output("devices")
	if err != nil {
		return nil, err
	}
	var devices []string
	for _, line := range strings.Split(string(output), "\n") {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "List of devices") || strings.HasPrefix(line, "*") {
			continue
		}
		fields := strings.Fields(line)
		if len(fields) >= 2 && fields[1] == "device" {
			devices = append(devices, fields[0])
		}
	}
	return devices, nil
}

func (b *adbBridge) shell(parts ...string) ([]byte, error) {
	if err := b.ensureSerial(); err != nil {
		return nil, err
	}
	args := append([]string{"-s", b.serial, "shell"}, parts...)
	return b.runner.output(args...)
}

func (b *adbBridge) execOut(parts ...string) ([]byte, error) {
	if err := b.ensureSerial(); err != nil {
		return nil, err
	}
	args := append([]string{"-s", b.serial, "exec-out"}, parts...)
	return b.runner.output(args...)
}

// --- list_apps ---

func (b *adbBridge) listApps() (string, error) {
	packages, err := b.launchablePackages()
	if err != nil {
		return "", err
	}
	foreground, _, _ := b.foregroundApp()

	var lines []string
	if foreground != "" {
		lines = append(lines, "Running in foreground: "+foreground, "")
	}
	lines = append(lines, "Launchable apps:")
	for _, pkg := range packages {
		marker := ""
		if pkg == foreground {
			marker = " (foreground)"
		}
		lines = append(lines, "- "+pkg+marker)
	}
	return strings.Join(lines, "\n"), nil
}

func (b *adbBridge) launchablePackages() ([]string, error) {
	output, err := b.shell("cmd", "package", "query-activities", "--brief",
		"-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER")
	if err != nil {
		// Older Android versions miss `cmd package query-activities`; fall back
		// to the full package list so app resolution still works.
		output, err = b.shell("pm", "list", "packages")
		if err != nil {
			return nil, err
		}
		return parsePackageList(string(output)), nil
	}
	return parseQueryActivities(string(output)), nil
}

func parseQueryActivities(output string) []string {
	seen := map[string]bool{}
	var packages []string
	for _, line := range strings.Split(output, "\n") {
		line = strings.TrimSpace(line)
		slash := strings.Index(line, "/")
		if slash <= 0 || strings.ContainsAny(line[:slash], " \t:") {
			continue
		}
		pkg := line[:slash]
		if !seen[pkg] {
			seen[pkg] = true
			packages = append(packages, pkg)
		}
	}
	sort.Strings(packages)
	return packages
}

func parsePackageList(output string) []string {
	seen := map[string]bool{}
	var packages []string
	for _, line := range strings.Split(output, "\n") {
		line = strings.TrimSpace(line)
		pkg, found := strings.CutPrefix(line, "package:")
		if !found || pkg == "" {
			continue
		}
		if !seen[pkg] {
			seen[pkg] = true
			packages = append(packages, pkg)
		}
	}
	sort.Strings(packages)
	return packages
}

// --- app resolution and foreground handling ---

var resumedActivityPattern = regexp.MustCompile(`(?:mResumedActivity|topResumedActivity)[^\n]*?\s([A-Za-z0-9_.]+)/(\S+?)[}\s]`)
var currentFocusPattern = regexp.MustCompile(`mCurrentFocus=Window\{\S+\s+\S+\s+([A-Za-z0-9_.]+)/(\S+?)\}`)

func (b *adbBridge) foregroundApp() (pkg, activity string, err error) {
	output, err := b.shell("dumpsys", "activity", "activities")
	if err == nil {
		if match := resumedActivityPattern.FindStringSubmatch(string(output)); match != nil {
			return match[1], match[2], nil
		}
	}
	output, err = b.shell("dumpsys", "window", "windows")
	if err != nil {
		return "", "", err
	}
	if match := currentFocusPattern.FindStringSubmatch(string(output)); match != nil {
		return match[1], match[2], nil
	}
	return "", "", errors.New("Could not determine the foreground app from dumpsys.")
}

func (b *adbBridge) resolvePackage(query string) (string, error) {
	query = strings.TrimSpace(query)
	lowered := strings.ToLower(query)
	if lowered == "foreground" || lowered == "current" {
		pkg, _, err := b.foregroundApp()
		if err != nil {
			return "", err
		}
		return pkg, nil
	}
	packages, err := b.launchablePackages()
	if err != nil {
		return "", err
	}
	if pkg := matchPackage(packages, query); pkg != "" {
		return pkg, nil
	}
	// The query may name a non-launcher package (e.g. com.android.systemui).
	output, err := b.shell("pm", "list", "packages")
	if err == nil {
		if pkg := matchPackage(parsePackageList(string(output)), query); pkg != "" {
			return pkg, nil
		}
	}
	return "", fmt.Errorf("appNotFound(%q): no installed package matches. Use list_apps to see available apps.", query)
}

func matchPackage(packages []string, query string) string {
	lowered := strings.ToLower(strings.TrimSpace(query))
	if lowered == "" {
		return ""
	}
	condensed := strings.ReplaceAll(lowered, " ", "")
	var exact, segment, contains []string
	for _, pkg := range packages {
		loweredPkg := strings.ToLower(pkg)
		if loweredPkg == lowered {
			exact = append(exact, pkg)
			continue
		}
		segments := strings.Split(loweredPkg, ".")
		if segments[len(segments)-1] == condensed {
			segment = append(segment, pkg)
			continue
		}
		if strings.Contains(loweredPkg, condensed) {
			contains = append(contains, pkg)
		}
	}
	for _, candidates := range [][]string{exact, segment, contains} {
		if len(candidates) > 0 {
			best := candidates[0]
			for _, candidate := range candidates[1:] {
				if len(candidate) < len(best) {
					best = candidate
				}
			}
			return best
		}
	}
	return ""
}

func (b *adbBridge) ensureForeground(pkg string) error {
	current, _, err := b.foregroundApp()
	if err == nil && current == pkg {
		return nil
	}
	if _, err := b.shell("monkey", "-p", pkg, "-c", "android.intent.category.LAUNCHER", "1"); err != nil {
		return fmt.Errorf("Could not launch %s: %s", pkg, err)
	}
	deadline := time.Now().Add(launchWaitTimeout)
	for time.Now().Before(deadline) {
		b.sleep(500 * time.Millisecond)
		current, _, err = b.foregroundApp()
		if err == nil && current == pkg {
			return nil
		}
	}
	return fmt.Errorf("Launched %s but it did not reach the foreground within %s.", pkg, launchWaitTimeout)
}

// --- snapshot pipeline ---

func (b *adbBridge) appState(app string) (*appSnapshot, error) {
	pkg, err := b.resolvePackage(app)
	if err != nil {
		return nil, err
	}
	if err := b.ensureForeground(pkg); err != nil {
		return nil, err
	}
	return b.captureSnapshot(pkg)
}

func (b *adbBridge) captureSnapshot(pkg string) (*appSnapshot, error) {
	if companionEnabled() {
		if snapshot, err := b.captureSnapshotViaCompanion(pkg); err == nil {
			return snapshot, nil
		}
		// Companion problems fall back to the uiautomator path: a worse
		// snapshot beats no snapshot, and doctor reports the companion state.
	}

	_, activity, _ := b.foregroundApp()

	// Capture the screenshot first: its downsampling scale defines the
	// coordinate space the accessibility tree is rendered in.
	scale := 1.0
	screenshot := ""
	var windowBounds *frame
	if png, err := b.execOut("screencap", "-p"); err == nil && len(png) > 0 {
		if scaled, appliedScale, width, height, encodeErr := downscalePNG(png, imageConfigFromEnv()); encodeErr == nil {
			screenshot = base64.StdEncoding.EncodeToString(scaled)
			scale = appliedScale
			windowBounds = &frame{X: 0, Y: 0, Width: float64(width), Height: float64(height)}
		}
	}

	dump, err := b.uiautomatorDump()
	if err != nil {
		return nil, err
	}
	tree, elements, focused, err := parseUIHierarchy(dump, pkg, scale)
	if err != nil {
		return nil, err
	}
	if windowBounds == nil {
		var maxX, maxY float64
		for _, record := range elements {
			if record.Frame != nil {
				maxX = math.Max(maxX, record.Frame.X+record.Frame.Width)
				maxY = math.Max(maxY, record.Frame.Y+record.Frame.Height)
			}
		}
		if maxX > 0 && maxY > 0 {
			windowBounds = &frame{X: 0, Y: 0, Width: maxX, Height: maxY}
		}
	}

	return &appSnapshot{
		App: appDescriptor{
			Name:             packageLabel(pkg),
			BundleIdentifier: pkg,
			PID:              b.packagePID(pkg),
		},
		WindowTitle:         activity,
		WindowBounds:        windowBounds,
		ScreenshotPNGBase64: screenshot,
		TreeLines:           tree,
		Elements:            elements,
		FocusedSummary:      focused,
		CoordinateScale:     scale,
	}, nil
}

func (b *adbBridge) uiautomatorDump() (string, error) {
	dump, err := b.uiautomatorDumpOnce()
	if err != nil {
		// uiautomator dump is flaky on some ROMs; retry once before failing.
		b.sleep(500 * time.Millisecond)
		dump, err = b.uiautomatorDumpOnce()
	}
	return dump, err
}

func (b *adbBridge) uiautomatorDumpOnce() (string, error) {
	if _, err := b.shell("uiautomator", "dump", dumpDevicePath); err != nil {
		return "", fmt.Errorf("uiautomator dump failed: %s", err)
	}
	output, err := b.shell("cat", dumpDevicePath)
	if err != nil {
		return "", err
	}
	_, _ = b.shell("rm", "-f", dumpDevicePath)
	text := string(output)
	if !strings.Contains(text, "<hierarchy") {
		return "", errors.New("uiautomator returned an empty accessibility hierarchy.")
	}
	return text, nil
}

func (b *adbBridge) packagePID(pkg string) int {
	output, err := b.shell("pidof", "-s", pkg)
	if err != nil {
		return 0
	}
	pid, err := strconv.Atoi(strings.TrimSpace(string(output)))
	if err != nil {
		return 0
	}
	return pid
}

func packageLabel(pkg string) string {
	segments := strings.Split(pkg, ".")
	return segments[len(segments)-1]
}

// captureSnapshotViaCompanion builds an appSnapshot from the companion's live
// accessibility tree and (where supported) its screenshot, with adb screencap
// as the image fallback. Output format matches the uiautomator path exactly.
func (b *adbBridge) captureSnapshotViaCompanion(pkg string) (*appSnapshot, error) {
	companion := b.companionLink()
	tree, err := companion.snapshotTree()
	if err != nil {
		return nil, err
	}

	png, screenshotErr := companion.screenshotPNG()
	if screenshotErr != nil || len(png) == 0 {
		png, _ = b.execOut("screencap", "-p")
	}
	scale := 1.0
	screenshot := ""
	var windowBounds *frame
	if len(png) > 0 {
		if scaled, appliedScale, width, height, encodeErr := downscalePNG(png, imageConfigFromEnv()); encodeErr == nil {
			screenshot = base64.StdEncoding.EncodeToString(scaled)
			scale = appliedScale
			windowBounds = &frame{X: 0, Y: 0, Width: float64(width), Height: float64(height)}
		}
	}

	treeLines, elements, focused := flattenCompanionTree(tree.Tree, scale)
	snapshotPkg := tree.Package
	if snapshotPkg == "" {
		snapshotPkg = pkg
	}
	_, activity, _ := b.foregroundApp()

	return &appSnapshot{
		App: appDescriptor{
			Name:             packageLabel(snapshotPkg),
			BundleIdentifier: snapshotPkg,
			PID:              b.packagePID(snapshotPkg),
		},
		WindowTitle:         activity,
		WindowBounds:        windowBounds,
		ScreenshotPNGBase64: screenshot,
		TreeLines:           treeLines,
		Elements:            elements,
		FocusedSummary:      focused,
		CoordinateScale:     scale,
	}, nil
}

// --- uiautomator XML parsing ---

type uiNode struct {
	Text          string   `xml:"text,attr"`
	ResourceID    string   `xml:"resource-id,attr"`
	Class         string   `xml:"class,attr"`
	Package       string   `xml:"package,attr"`
	ContentDesc   string   `xml:"content-desc,attr"`
	Checkable     string   `xml:"checkable,attr"`
	Checked       string   `xml:"checked,attr"`
	Clickable     string   `xml:"clickable,attr"`
	Enabled       string   `xml:"enabled,attr"`
	Focusable     string   `xml:"focusable,attr"`
	Focused       string   `xml:"focused,attr"`
	Scrollable    string   `xml:"scrollable,attr"`
	LongClickable string   `xml:"long-clickable,attr"`
	Selected      string   `xml:"selected,attr"`
	Bounds        string   `xml:"bounds,attr"`
	Nodes         []uiNode `xml:"node"`
}

type uiHierarchy struct {
	Nodes []uiNode `xml:"node"`
}

var boundsPattern = regexp.MustCompile(`\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]`)

func parseBounds(value string) *frame {
	match := boundsPattern.FindStringSubmatch(value)
	if match == nil {
		return nil
	}
	left, _ := strconv.Atoi(match[1])
	top, _ := strconv.Atoi(match[2])
	right, _ := strconv.Atoi(match[3])
	bottom, _ := strconv.Atoi(match[4])
	if right <= left || bottom <= top {
		return nil
	}
	return &frame{X: float64(left), Y: float64(top), Width: float64(right - left), Height: float64(bottom - top)}
}

func isTrue(value string) bool {
	return value == "true"
}

func scaleFrame(f *frame, scale float64) *frame {
	if f == nil || scale == 1 {
		return f
	}
	return &frame{
		X:      math.Round(f.X * scale),
		Y:      math.Round(f.Y * scale),
		Width:  math.Round(f.Width * scale),
		Height: math.Round(f.Height * scale),
	}
}

func nodeActions(node uiNode) []string {
	var actions []string
	if isTrue(node.Clickable) {
		actions = append(actions, "click")
	}
	if isTrue(node.LongClickable) {
		actions = append(actions, "long-click")
	}
	if isTrue(node.Scrollable) {
		actions = append(actions, "scroll")
	}
	if isTrue(node.Checkable) {
		actions = append(actions, "check")
	}
	if isEditableClass(node.Class) {
		actions = append(actions, "set_value")
	}
	return actions
}

func isEditableClass(class string) bool {
	return strings.Contains(class, "EditText") || strings.Contains(class, "AutoCompleteTextView") || strings.Contains(class, "SearchView")
}

func shortClass(class string) string {
	segments := strings.Split(class, ".")
	return segments[len(segments)-1]
}

func nodeLabel(node uiNode) string {
	if strings.TrimSpace(node.Text) != "" {
		return node.Text
	}
	return node.ContentDesc
}

func interestingNode(node uiNode) bool {
	if !isTrue(node.Enabled) {
		return false
	}
	if isTrue(node.Clickable) || isTrue(node.LongClickable) || isTrue(node.Scrollable) ||
		isTrue(node.Checkable) || isTrue(node.Focused) || isEditableClass(node.Class) {
		return true
	}
	return strings.TrimSpace(nodeLabel(node)) != ""
}

// snapshotTreeBuilder renders element records into the indexed tree-line
// format shared by the uiautomator and companion snapshot sources.
type snapshotTreeBuilder struct {
	treeLines []string
	elements  []elementRecord
	focused   string
	nextIndex int
}

func newSnapshotTreeBuilder() *snapshotTreeBuilder {
	return &snapshotTreeBuilder{nextIndex: 1}
}

func (builder *snapshotTreeBuilder) add(record elementRecord, depth int, selected bool) {
	if len(builder.treeLines) >= maxTreeLines || record.Frame == nil {
		return
	}
	line := strings.Repeat("  ", depth)
	if len(record.Actions) > 0 && len(builder.elements) < maxElements {
		record.Index = builder.nextIndex
		builder.nextIndex++
		builder.elements = append(builder.elements, record)
		line += fmt.Sprintf("[%d] ", record.Index)
	} else {
		line += "- "
	}
	line += record.ControlType
	if record.Name != "" {
		line += fmt.Sprintf(" %q", record.Name)
	}
	if record.ResourceID != "" {
		line += " (id: " + record.ResourceID + ")"
	}
	line += " " + record.Frame.renderedLocalFrame()
	if len(record.Actions) > 0 {
		line += " [" + strings.Join(record.Actions, " ") + "]"
	}
	if selected {
		line += " (selected)"
	}
	builder.treeLines = append(builder.treeLines, line)
	if record.Focused {
		summary := record.ControlType
		if record.Name != "" {
			summary += fmt.Sprintf(" %q", record.Name)
		}
		if record.Index > 0 {
			summary += fmt.Sprintf(" (element %d)", record.Index)
		}
		builder.focused = summary
	}
}

// parseUIHierarchy renders the uiautomator dump into indexed tree lines and
// element records. Frames are converted from device pixels into screenshot
// pixel space using scale, so what the model sees matches the PNG.
func parseUIHierarchy(dump, pkg string, scale float64) (treeLines []string, elements []elementRecord, focusedSummary string, err error) {
	var hierarchy uiHierarchy
	if err := xml.Unmarshal([]byte(dump), &hierarchy); err != nil {
		return nil, nil, "", fmt.Errorf("Could not parse uiautomator XML: %w", err)
	}
	if scale <= 0 {
		scale = 1
	}

	builder := newSnapshotTreeBuilder()
	var walk func(node uiNode, depth int)
	walk = func(node uiNode, depth int) {
		frame := scaleFrame(parseBounds(node.Bounds), scale)
		include := interestingNode(node) && frame != nil && (pkg == "" || node.Package == "" || node.Package == pkg)
		if include {
			builder.add(elementRecord{
				ResourceID:  node.ResourceID,
				Name:        nodeLabel(node),
				ControlType: shortClass(node.Class),
				Value:       node.Text,
				Frame:       frame,
				Actions:     nodeActions(node),
				Focused:     isTrue(node.Focused),
			}, depth, isTrue(node.Checked) || isTrue(node.Selected))
		}
		childDepth := depth
		if include {
			childDepth++
		}
		for _, child := range node.Nodes {
			walk(child, childDepth)
		}
	}
	for _, node := range hierarchy.Nodes {
		walk(node, 0)
	}
	return builder.treeLines, builder.elements, builder.focused, nil
}

// flattenCompanionTree renders a companion protocol-v1 tree into the same
// indexed tree-line format as parseUIHierarchy.
func flattenCompanionTree(root *companionNode, scale float64) (treeLines []string, elements []elementRecord, focusedSummary string) {
	if scale <= 0 {
		scale = 1
	}
	builder := newSnapshotTreeBuilder()
	var walk func(node companionNode, depth int)
	walk = func(node companionNode, depth int) {
		frame := companionFrame(node.Bounds, scale)
		include := companionInteresting(node) && frame != nil
		if include {
			builder.add(elementRecord{
				ResourceID:  node.ResourceID,
				Name:        companionLabel(node),
				ControlType: shortClass(node.ClassName),
				Value:       node.Text,
				Frame:       frame,
				Actions:     companionActions(node),
				Focused:     node.Focused,
			}, depth, node.Checked || node.Selected)
		}
		childDepth := depth
		if include {
			childDepth++
		}
		for _, child := range node.Children {
			walk(child, childDepth)
		}
	}
	if root != nil {
		walk(*root, 0)
	}
	return builder.treeLines, builder.elements, builder.focused
}

func companionFrame(bounds []int, scale float64) *frame {
	if len(bounds) != 4 || bounds[2] <= bounds[0] || bounds[3] <= bounds[1] {
		return nil
	}
	device := &frame{
		X:      float64(bounds[0]),
		Y:      float64(bounds[1]),
		Width:  float64(bounds[2] - bounds[0]),
		Height: float64(bounds[3] - bounds[1]),
	}
	return scaleFrame(device, scale)
}

func companionLabel(node companionNode) string {
	if strings.TrimSpace(node.Text) != "" {
		return node.Text
	}
	return node.ContentDesc
}

func companionActions(node companionNode) []string {
	var actions []string
	if node.Clickable {
		actions = append(actions, "click")
	}
	if node.LongClickable {
		actions = append(actions, "long-click")
	}
	if node.Scrollable {
		actions = append(actions, "scroll")
	}
	if node.Checkable {
		actions = append(actions, "check")
	}
	if node.Editable {
		actions = append(actions, "set_value")
	}
	return actions
}

func companionInteresting(node companionNode) bool {
	if !node.isEnabled() {
		return false
	}
	if node.Clickable || node.LongClickable || node.Scrollable || node.Checkable || node.Focused || node.Editable {
		return true
	}
	return strings.TrimSpace(companionLabel(node)) != ""
}

// --- actions ---

func (b *adbBridge) action(request actionRequest) (*appSnapshot, error) {
	pkg := request.Package
	if pkg == "" {
		resolved, err := b.resolvePackage(request.App)
		if err != nil {
			return nil, err
		}
		pkg = resolved
	}
	if err := b.performAction(request); err != nil {
		return nil, err
	}
	b.sleep(actionSettleDelay)
	return b.captureSnapshot(pkg)
}

func (b *adbBridge) performAction(request actionRequest) error {
	switch request.Tool {
	case "click":
		return b.performClick(request)
	case "perform_secondary_action":
		return b.performSecondary(request)
	case "scroll":
		return b.performScroll(request)
	case "drag":
		return b.performDrag(request)
	case "type_text":
		return b.performTypeText(request.Text)
	case "press_key":
		return b.performPressKey(request.Key)
	case "set_value":
		return b.performSetValue(request)
	default:
		return fmt.Errorf("unsupportedTool(%q)", request.Tool)
	}
}

// actionPoint resolves the request target to device pixel coordinates.
func actionPoint(request actionRequest) (int, int, error) {
	if request.Element != nil && request.Element.Frame != nil {
		f := request.Element.Frame
		x, y := deviceCoordinates(f.X+f.Width/2, f.Y+f.Height/2, request.Scale)
		return x, y, nil
	}
	if request.X == nil || request.Y == nil {
		return 0, 0, errors.New("click requires either element_index or x/y")
	}
	x, y := deviceCoordinates(*request.X, *request.Y, request.Scale)
	return x, y, nil
}

func deviceCoordinates(x, y, scale float64) (int, int) {
	if scale <= 0 {
		scale = 1
	}
	return int(math.Round(x / scale)), int(math.Round(y / scale))
}

// companionGesture tries the companion when enabled; true means it handled the
// gesture. Errors fall back to ADB input synthesis, which covers the same
// gesture surface.
func (b *adbBridge) companionGesture(action map[string]any) bool {
	if !companionEnabled() {
		return false
	}
	return b.companionLink().gesture(action) == nil
}

func (b *adbBridge) tap(x, y int) error {
	if b.companionGesture(map[string]any{"type": "tap", "x": x, "y": y}) {
		return nil
	}
	_, err := b.shell("input", "tap", strconv.Itoa(x), strconv.Itoa(y))
	return err
}

func (b *adbBridge) swipeGesture(fromX, fromY, toX, toY, durationMs int) error {
	if b.companionGesture(map[string]any{
		"type": "swipe", "fromX": fromX, "fromY": fromY, "toX": toX, "toY": toY, "durationMs": durationMs,
	}) {
		return nil
	}
	_, err := b.shell("input", "swipe",
		strconv.Itoa(fromX), strconv.Itoa(fromY), strconv.Itoa(toX), strconv.Itoa(toY), strconv.Itoa(durationMs))
	return err
}

func (b *adbBridge) performClick(request actionRequest) error {
	x, y, err := actionPoint(request)
	if err != nil {
		return err
	}
	switch strings.ToLower(request.MouseButton) {
	case "", "left":
		count := request.ClickCount
		if count < 1 {
			count = 1
		}
		for tap := 0; tap < count; tap++ {
			if tap > 0 {
				b.sleep(120 * time.Millisecond)
			}
			if err := b.tap(x, y); err != nil {
				return err
			}
		}
		return nil
	case "right":
		return b.longPress(x, y)
	default:
		return fmt.Errorf("mouse_button %q is not supported on Android. Use left, or right for a long-press.", request.MouseButton)
	}
}

func (b *adbBridge) longPress(x, y int) error {
	if b.companionGesture(map[string]any{"type": "longPress", "x": x, "y": y}) {
		return nil
	}
	point := []string{strconv.Itoa(x), strconv.Itoa(y)}
	_, err := b.shell(append([]string{"input", "swipe"}, append(point, append(point, "600")...)...)...)
	return err
}

func (b *adbBridge) performSecondary(request actionRequest) error {
	if request.Element == nil || request.Element.Frame == nil {
		return errors.New("perform_secondary_action requires an element with a frame")
	}
	action := strings.ToLower(strings.TrimSpace(request.Action))
	if action != "long-click" && action != "long_click" && action != "longclick" {
		return fmt.Errorf("%q is not a valid secondary action for element %d. Available: long-click.", request.Action, request.Element.Index)
	}
	if !containsAction(request.Element.Actions, "long-click") {
		return fmt.Errorf("Element %d does not expose a long-click action.", request.Element.Index)
	}
	f := request.Element.Frame
	x, y := deviceCoordinates(f.X+f.Width/2, f.Y+f.Height/2, request.Scale)
	return b.longPress(x, y)
}

func containsAction(actions []string, action string) bool {
	for _, candidate := range actions {
		if candidate == action {
			return true
		}
	}
	return false
}

// scrollSwipe computes one swipe gesture (in device pixels) that scrolls the
// element one "page" in the requested reading direction: scrolling down moves
// the finger up, etc. The fraction scales the swipe distance for partial pages.
func scrollSwipe(f frame, scale float64, direction string, fraction float64) (fromX, fromY, toX, toY int) {
	if fraction <= 0 {
		fraction = 1
	}
	if fraction > 1 {
		fraction = 1
	}
	centerX := f.X + f.Width/2
	centerY := f.Y + f.Height/2
	spanX := f.Width * 0.35 * fraction
	spanY := f.Height * 0.35 * fraction
	var x1, y1, x2, y2 float64
	switch direction {
	case "down":
		x1, y1, x2, y2 = centerX, centerY+spanY, centerX, centerY-spanY
	case "up":
		x1, y1, x2, y2 = centerX, centerY-spanY, centerX, centerY+spanY
	case "right":
		x1, y1, x2, y2 = centerX+spanX, centerY, centerX-spanX, centerY
	case "left":
		x1, y1, x2, y2 = centerX-spanX, centerY, centerX+spanX, centerY
	}
	fromX, fromY = deviceCoordinates(x1, y1, scale)
	toX, toY = deviceCoordinates(x2, y2, scale)
	return fromX, fromY, toX, toY
}

func (b *adbBridge) performScroll(request actionRequest) error {
	if request.Element == nil || request.Element.Frame == nil {
		return errors.New("scroll requires an element with a frame")
	}
	pages := request.Pages
	if pages <= 0 {
		pages = 1
	}
	const maxSwipes = 20
	swipes := 0
	for pages > 0 && swipes < maxSwipes {
		fraction := math.Min(pages, 1)
		fromX, fromY, toX, toY := scrollSwipe(*request.Element.Frame, request.Scale, request.Direction, fraction)
		if err := b.swipeGesture(fromX, fromY, toX, toY, 300); err != nil {
			return err
		}
		pages -= fraction
		swipes++
		if pages > 0 {
			b.sleep(250 * time.Millisecond)
		}
	}
	return nil
}

func (b *adbBridge) performDrag(request actionRequest) error {
	if request.FromX == nil || request.FromY == nil || request.ToX == nil || request.ToY == nil {
		return errors.New("drag requires from_x, from_y, to_x, and to_y")
	}
	fromX, fromY := deviceCoordinates(*request.FromX, *request.FromY, request.Scale)
	toX, toY := deviceCoordinates(*request.ToX, *request.ToY, request.Scale)
	if b.companionGesture(map[string]any{
		"type": "swipe", "fromX": fromX, "fromY": fromY, "toX": toX, "toY": toY, "durationMs": 800,
	}) {
		return nil
	}
	coordinates := []string{strconv.Itoa(fromX), strconv.Itoa(fromY), strconv.Itoa(toX), strconv.Itoa(toY), "800"}
	if _, err := b.shell(append([]string{"input", "draganddrop"}, coordinates...)...); err == nil {
		return nil
	}
	_, err := b.shell(append([]string{"input", "swipe"}, coordinates...)...)
	return err
}

func (b *adbBridge) performTypeText(text string) error {
	if companionEnabled() {
		companionErr := b.companionLink().setText(text)
		if companionErr == nil {
			return nil
		}
		// Fall back to the ADB path only when it can represent the text;
		// otherwise the companion error (which names the fix) wins.
		if _, asciiErr := escapeInputText(text); asciiErr != nil {
			return companionErr
		}
	}
	escaped, err := escapeInputText(text)
	if err != nil {
		return err
	}
	_, err = b.shell("input", "text", escaped)
	return err
}

// escapeInputText prepares literal text for `adb shell input text`. The
// argument crosses a device-side shell, so shell metacharacters are escaped
// and spaces become %s (the encoding `input text` defines for spaces).
// `input text` cannot deliver non-ASCII reliably, so reject it loudly.
func escapeInputText(text string) (string, error) {
	for _, r := range text {
		if r > 126 || (r < 32 && r != '\t') {
			return "", fmt.Errorf("type_text over ADB supports printable ASCII only; %q cannot be typed. Non-ASCII input requires the on-device companion runtime.", string(r))
		}
	}
	var builder strings.Builder
	for _, r := range text {
		switch r {
		case ' ':
			builder.WriteString("%s")
		case '\t':
			builder.WriteString("\\t")
		case '\\', '\'', '"', '`', '$', '&', '|', ';', '(', ')', '<', '>', '*', '?', '~', '#':
			builder.WriteRune('\\')
			builder.WriteRune(r)
		default:
			builder.WriteRune(r)
		}
	}
	return builder.String(), nil
}

func (b *adbBridge) performPressKey(key string) error {
	codes, err := parseKeySpec(key)
	if err != nil {
		return err
	}
	if len(codes) == 1 {
		_, err = b.shell("input", "keyevent", strconv.Itoa(codes[0]))
		return err
	}
	args := []string{"input", "keycombination"}
	for _, code := range codes {
		args = append(args, strconv.Itoa(code))
	}
	if _, err = b.shell(args...); err != nil {
		return fmt.Errorf("Key combination %q failed (input keycombination needs Android 13+): %s", key, err)
	}
	return nil
}

func (b *adbBridge) performSetValue(request actionRequest) error {
	if request.Element == nil || request.Element.Frame == nil {
		return errors.New("set_value requires an element with a frame")
	}
	if !containsAction(request.Element.Actions, "set_value") {
		return fmt.Errorf("Element %d is not a settable text element.", request.Element.Index)
	}
	f := request.Element.Frame
	x, y := deviceCoordinates(f.X+f.Width/2, f.Y+f.Height/2, request.Scale)
	if err := b.tap(x, y); err != nil {
		return err
	}
	b.sleep(250 * time.Millisecond)
	if companionEnabled() {
		// ACTION_SET_TEXT replaces the whole value and carries full Unicode.
		companionErr := b.companionLink().setText(request.Value)
		if companionErr == nil {
			return nil
		}
		if _, asciiErr := escapeInputText(request.Value); asciiErr != nil {
			return companionErr
		}
	}
	escaped, err := escapeInputText(request.Value)
	if err != nil {
		return err
	}
	// Best-effort clear: select-all (ctrl+a, Android 13+) then delete. On older
	// devices the combination fails silently and the value is appended instead.
	_, _ = b.shell("input", "keycombination", "113", "29")
	_, _ = b.shell("input", "keyevent", "67")
	_, err = b.shell("input", "text", escaped)
	return err
}

// --- host diagnostics ---

func doctorReport() string {
	bridge := newADBBridge()
	var lines []string
	lines = append(lines, "Android runtime: drives a connected device or emulator over adb using the")
	lines = append(lines, "uiautomator accessibility snapshot plus input synthesis.")
	lines = append(lines, "")

	version, err := bridge.runner.output("version")
	if err != nil {
		lines = append(lines, "adb: NOT FOUND ("+err.Error()+")")
		lines = append(lines, "Install Android platform-tools and ensure adb is on PATH, or set OPEN_ANDROID_USE_ADB.")
		return strings.Join(lines, "\n")
	}
	lines = append(lines, "adb: "+strings.TrimSpace(strings.Split(string(version), "\n")[0]))

	devices, err := connectedDevices(bridge.runner)
	switch {
	case err != nil:
		lines = append(lines, "devices: error ("+err.Error()+")")
	case len(devices) == 0:
		lines = append(lines, "devices: none connected. Enable USB debugging and connect a device, or start an emulator.")
	default:
		lines = append(lines, "devices: "+strings.Join(devices, ", "))
		if err := bridge.ensureSerial(); err != nil {
			lines = append(lines, "selection: "+err.Error())
		} else {
			lines = append(lines, "selected: "+bridge.serial)
			if pkg, activity, err := bridge.foregroundApp(); err == nil {
				lines = append(lines, "foreground: "+pkg+"/"+activity)
			}
			lines = append(lines, bridge.companionLink().describe())
		}
	}
	return strings.Join(lines, "\n")
}

func devicesReport() (string, error) {
	bridge := newADBBridge()
	devices, err := connectedDevices(bridge.runner)
	if err != nil {
		return "", err
	}
	if len(devices) == 0 {
		return "No Android devices connected.", nil
	}
	return strings.Join(devices, "\n"), nil
}

#!/usr/bin/env node

// Builds the standalone `open-android-use` npm package: the host-side Android
// bridge (Go) for every supported dev-machine platform, plus a Node launcher
// that picks the right binary at run time. Publishing is a manual step:
//
//   node ./scripts/npm/build-android-package.mjs
//   npm publish dist/npm/open-android-use   # requires npm credentials
//
// Mirrors scripts/npm/build-packages.mjs (the desktop meta-package) but stays
// a separate, single-purpose builder: the bridge has no app bundle, no
// installer helpers, and its own version source (apps/OpenAndroidUse/main.go).

import { spawnSync } from "node:child_process";
import {
  chmodSync,
  cpSync,
  existsSync,
  mkdirSync,
  readFileSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "..", "..");
const packageName = "open-android-use";
const defaultOutDir = path.join(repoRoot, "dist", "npm");

const runtimeTargets = [
  { os: "darwin", cpu: "arm64", buildOS: "darwin", buildArch: "arm64", binary: "open-android-use" },
  { os: "darwin", cpu: "x64", buildOS: "darwin", buildArch: "amd64", binary: "open-android-use" },
  { os: "linux", cpu: "arm64", buildOS: "linux", buildArch: "arm64", binary: "open-android-use" },
  { os: "linux", cpu: "x64", buildOS: "linux", buildArch: "amd64", binary: "open-android-use" },
  { os: "win32", cpu: "arm64", buildOS: "windows", buildArch: "arm64", binary: "open-android-use.exe" },
  { os: "win32", cpu: "x64", buildOS: "windows", buildArch: "amd64", binary: "open-android-use.exe" },
];

function parseArgs(argv) {
  const options = { outDir: defaultOutDir, skipBuild: false };
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    switch (arg) {
      case "--out-dir":
        options.outDir = path.resolve(repoRoot, argv[index + 1]);
        index += 1;
        break;
      case "--skip-build":
        options.skipBuild = true;
        break;
      case "-h":
      case "--help":
        process.stdout.write(
          "Usage: node ./scripts/npm/build-android-package.mjs [--out-dir <dir>] [--skip-build]\n",
        );
        process.exit(0);
      default:
        throw new Error(`Unknown argument: ${arg}`);
    }
  }
  return options;
}

function run(command, args) {
  const result = spawnSync(command, args, { cwd: repoRoot, stdio: "inherit" });
  if (result.status !== 0) {
    throw new Error(`${command} ${args.join(" ")} failed with exit code ${result.status ?? "unknown"}`);
  }
}

function bridgeVersion() {
  const source = readFileSync(path.join(repoRoot, "apps", "OpenAndroidUse", "main.go"), "utf-8");
  const match = source.match(/var version = "([^"]+)"/);
  if (!match) {
    throw new Error("Could not read the bridge version from apps/OpenAndroidUse/main.go");
  }
  return match[1];
}

function builtBinaryPath(target) {
  return path.join(repoRoot, "dist", "android-bridge", target.buildOS, target.buildArch, target.binary);
}

function ensureBuilt(options) {
  if (options.skipBuild) {
    return;
  }
  for (const target of runtimeTargets) {
    run(path.join(repoRoot, "scripts", "build-open-android-use.sh"), [
      "--os",
      target.buildOS,
      "--arch",
      target.buildArch,
    ]);
  }
}

function renderLauncher() {
  const platformTable = Object.fromEntries(
    runtimeTargets.map((target) => [
      `${target.os}-${target.cpu}`,
      ["runtimes", `${target.os}-${target.cpu}`, target.binary],
    ]),
  );

  return `#!/usr/bin/env node
// Thin platform selector: every command and option is passed through to the
// bundled native bridge (run \`open-android-use help\` for the CLI surface).
const { spawn } = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");

const platformBinaries = ${JSON.stringify(platformTable, null, 2)};
const packageRoot = path.resolve(__dirname, "..");
const platformKey = \`\${process.platform}-\${process.arch}\`;
const relativePath = platformBinaries[platformKey];

if (!relativePath) {
  console.error(
    \`Unsupported platform \${platformKey}. Supported: \${Object.keys(platformBinaries).sort().join(", ")}.\`,
  );
  process.exit(1);
}

const executable = path.join(packageRoot, ...relativePath);
if (!fs.existsSync(executable)) {
  console.error(\`Missing bundled bridge for \${platformKey} at \${executable}.

Reinstall with:
  npm install -g ${packageName}\`);
  process.exit(1);
}

const child = spawn(executable, process.argv.slice(2), { stdio: "inherit", windowsHide: false });
child.on("error", (error) => {
  console.error(\`Failed to start \${executable}: \${error.message}\`);
  process.exit(1);
});
for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => child.kill(signal));
}
child.on("exit", (code, signal) => {
  process.exit(signal ? 1 : code ?? 0);
});
`;
}

function renderReadme(version) {
  return `# open-android-use

A second pair of hands for your Android device: this package ships the
host-side bridge (version ${version}) that exposes a connected phone or
emulator to any MCP client through the 9-tool Computer Use surface
(\`list_apps\`, \`get_app_state\`, \`click\`, \`perform_secondary_action\`,
\`scroll\`, \`drag\`, \`type_text\`, \`press_key\`, \`set_value\`).

Requires [Android platform-tools](https://developer.android.com/tools/releases/platform-tools)
(\`adb\`) on PATH and a device or emulator with USB debugging enabled.

\`\`\`sh
npm install -g open-android-use

open-android-use doctor       # check adb, device, and companion status
open-android-use mcp          # start the stdio MCP server
open-android-use help         # full CLI surface
\`\`\`

The optional on-device companion app (live accessibility tree, full-Unicode
typing, on-device agent) and full documentation live in the repository:
https://github.com/blokzdev/open-android-use
`;
}

function buildPackage(options) {
  const version = bridgeVersion();
  const packageDir = path.join(options.outDir, packageName);
  rmSync(packageDir, { recursive: true, force: true });
  mkdirSync(path.join(packageDir, "bin"), { recursive: true });

  for (const target of runtimeTargets) {
    const source = builtBinaryPath(target);
    if (!existsSync(source)) {
      throw new Error(`Missing built bridge binary: ${source} (run without --skip-build)`);
    }
    const destination = path.join(packageDir, "runtimes", `${target.os}-${target.cpu}`, target.binary);
    mkdirSync(path.dirname(destination), { recursive: true });
    cpSync(source, destination);
    chmodSync(destination, 0o755);
  }

  const launcherPath = path.join(packageDir, "bin", `${packageName}.js`);
  writeFileSync(launcherPath, renderLauncher(), "utf-8");
  chmodSync(launcherPath, 0o755);

  writeFileSync(
    path.join(packageDir, "package.json"),
    `${JSON.stringify(
      {
        name: packageName,
        version,
        description:
          "Computer Use for Android: host-side MCP bridge driving a phone or emulator over adb.",
        license: "MIT",
        repository: {
          type: "git",
          url: "git+https://github.com/blokzdev/open-android-use.git",
        },
        bin: { [packageName]: `bin/${packageName}.js` },
        files: ["bin", "runtimes", "README.md"],
        os: ["darwin", "linux", "win32"],
        cpu: ["arm64", "x64"],
        engines: { node: ">=18" },
        keywords: ["android", "mcp", "computer-use", "adb", "accessibility", "agent"],
      },
      null,
      2,
    )}\n`,
    "utf-8",
  );

  writeFileSync(path.join(packageDir, "README.md"), renderReadme(version), "utf-8");
  process.stdout.write(`Built ${packageName}@${version} at ${packageDir}\n`);
  return packageDir;
}

const options = parseArgs(process.argv.slice(2));
ensureBuilt(options);
buildPackage(options);

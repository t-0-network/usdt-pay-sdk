import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { after, afterEach, beforeEach, mock, test } from "node:test";
import { createServer, publicKeyFromPrivateKey } from "@t-0/usdt-pay-sdk";
import {
  ConfigurationError,
  loadConfig,
  serverSetupError,
  startupFailureLines,
} from "../src/config.js";

// Every case runs in an empty directory: your own .env must not leak into these tests,
// and the notice for a missing .env is part of what they check.
const dir = mkdtempSync(join(tmpdir(), "starter-config-"));
process.chdir(dir);
after(() => rmSync(dir, { recursive: true, force: true }));
// Resolved after the chdir, the way loadConfig resolves it (on macOS the temp directory
// is behind a symlink, and the working directory is the real path).
const envPath = resolve(".env");

const PRIVATE_KEY = "0x4c0883a69102937d6231471b5dbb6204fe512961708279f23efb0fecd891d2f2";
const NETWORK_PUBLIC_KEY = publicKeyFromPrivateKey("0x" + "11".repeat(32));
const SANDBOX = "https://usdt-pay-api-sandbox.t-0.network";

const NETWORK_PUBLIC_KEY_HELP = "Ask the t-0 team for the network public key and put it in .env.";

const VARIABLES = ["PROVIDER_PRIVATE_KEY", "NETWORK_PUBLIC_KEY", "TZERO_ENDPOINT", "PORT"] as const;
type Env = Partial<Record<(typeof VARIABLES)[number], string>>;

const valid: Env = { PROVIDER_PRIVATE_KEY: PRIVATE_KEY, NETWORK_PUBLIC_KEY };

/** Sets exactly these of the starter's variables, and unsets the rest. */
function setEnv(env: Env): void {
  for (const name of VARIABLES) {
    const value = env[name];
    if (value === undefined) {
      delete process.env[name];
    } else {
      process.env[name] = value;
    }
  }
}

/** The configuration error loadConfig throws for this environment. */
function configError(env: Env): ConfigurationError {
  setEnv(env);
  try {
    loadConfig();
  } catch (error) {
    assert.ok(error instanceof ConfigurationError, `not a ConfigurationError: ${error}`);
    return error;
  }
  assert.fail("loadConfig accepted the configuration");
}

/** What `call` throws, synchronously. */
function thrownBy(call: () => unknown): Error {
  try {
    call();
  } catch (error) {
    assert.ok(error instanceof Error, `not an Error: ${error}`);
    return error;
  }
  assert.fail("it did not throw");
}

let stderr: string[];
beforeEach(() => {
  stderr = [];
  mock.method(console, "error", (...args: unknown[]) => {
    stderr.push(args.map(String).join(" "));
  });
});
afterEach(() => mock.restoreAll());

test("with no .env, it says on stderr where it looked", () => {
  setEnv(valid);

  loadConfig();

  assert.deepEqual(stderr, [
    `No .env at ${envPath} — taking configuration from the environment instead`,
  ]);
});

test("PROVIDER_PRIVATE_KEY not set", () => {
  const error = configError({ NETWORK_PUBLIC_KEY });

  assert.equal(error.message, "PROVIDER_PRIVATE_KEY is not set");
  assert.equal(
    error.help,
    `.env is read from the working directory, and we looked in ${envPath}. Run the app from the directory holding your .env, or set PROVIDER_PRIVATE_KEY in the environment. Only a project with no .env at all starts one from .env.example — an existing .env holds the key generated for you, and its private half is not recoverable.`,
  );
});

test("PROVIDER_PRIVATE_KEY of only whitespace is not set", () => {
  const error = configError({ PROVIDER_PRIVATE_KEY: " \t\n", NETWORK_PUBLIC_KEY });

  assert.equal(error.message, "PROVIDER_PRIVATE_KEY is not set");
});

test("PROVIDER_PRIVATE_KEY malformed", () => {
  const sdkError = thrownBy(() => publicKeyFromPrivateKey("0x1234"));

  const error = configError({ PROVIDER_PRIVATE_KEY: "0x1234", NETWORK_PUBLIC_KEY });

  assert.equal(error.message, `PROVIDER_PRIVATE_KEY is not usable: ${sdkError.message}`);
  assert.equal(error.help, "Any 32 random bytes will do: openssl rand -hex 32.");
});

test("NETWORK_PUBLIC_KEY not set", () => {
  const error = configError({ PROVIDER_PRIVATE_KEY: PRIVATE_KEY });

  assert.equal(error.message, "NETWORK_PUBLIC_KEY is not set");
  assert.equal(error.help, NETWORK_PUBLIC_KEY_HELP);
});

test("NETWORK_PUBLIC_KEY of only whitespace is not set", () => {
  const error = configError({ PROVIDER_PRIVATE_KEY: PRIVATE_KEY, NETWORK_PUBLIC_KEY: "  " });

  assert.equal(error.message, "NETWORK_PUBLIC_KEY is not set");
});

test("NETWORK_PUBLIC_KEY malformed: the SDK's refusal becomes a configuration error", () => {
  // createServer checks the key before it returns its promise, so nothing is bound.
  const thrown = thrownBy(() => createServer(0, "04zz", () => {}));
  assert.match(thrown.message, /^invalid network public key: /);

  const error = serverSetupError(thrown);

  assert.ok(error instanceof ConfigurationError);
  assert.equal(error.message, thrown.message);
  assert.equal(error.help, NETWORK_PUBLIC_KEY_HELP);
  assert.deepEqual(startupFailureLines("Issuer", error), [
    `ERROR: ${thrown.message}`,
    NETWORK_PUBLIC_KEY_HELP,
  ]);
});

test("any other server setup failure passes through unchanged", () => {
  const bind = new Error("listen EADDRINUSE: address already in use :::8080");

  assert.equal(serverSetupError(bind), bind);
});

for (const raw of [
  "0",
  "65536",
  "-1",
  "+80",
  "0x50",
  "1e3",
  "8_080",
  "80.0",
  "8080 x",
  "٨٠٨٠", // Arabic-Indic digits
  "99999999999999999999",
]) {
  test(`PORT=${JSON.stringify(raw)} is not a port`, () => {
    const error = configError({ ...valid, PORT: raw });

    assert.equal(error.message, `PORT is not a valid port number: ${raw}`);
    assert.equal(
      error.help,
      "Set PORT to an integer between 1 and 65535, or leave it unset for 8080.",
    );
  });
}

test("the PORT message shows the value as read, untrimmed", () => {
  const error = configError({ ...valid, PORT: " 0 " });

  assert.equal(error.message, "PORT is not a valid port number:  0 ");
});

for (const [raw, port] of [
  [undefined, 8080],
  ["", 8080],
  ["  ", 8080],
  [" 9090 ", 9090],
  ["1", 1],
  ["65535", 65535],
] as const) {
  test(`PORT=${JSON.stringify(raw)} listens on ${port}`, () => {
    setEnv({ ...valid, PORT: raw });

    assert.equal(loadConfig().port, port);
  });
}

test("TZERO_ENDPOINT unset or empty is the sandbox", () => {
  setEnv(valid);
  assert.equal(loadConfig().tzeroEndpoint, SANDBOX);

  setEnv({ ...valid, TZERO_ENDPOINT: "" });
  assert.equal(loadConfig().tzeroEndpoint, SANDBOX);

  setEnv({ ...valid, TZERO_ENDPOINT: "http://127.0.0.1:9000" });
  assert.equal(loadConfig().tzeroEndpoint, "http://127.0.0.1:9000");
});

test("the keys are used without their surrounding whitespace", () => {
  setEnv({
    PROVIDER_PRIVATE_KEY: ` ${PRIVATE_KEY}\n`,
    NETWORK_PUBLIC_KEY: `\t${NETWORK_PUBLIC_KEY} `,
  });

  const config = loadConfig();

  assert.equal(config.privateKey, PRIVATE_KEY);
  assert.equal(config.networkPublicKey, NETWORK_PUBLIC_KEY);
  assert.equal(config.publicKey, publicKeyFromPrivateKey(PRIVATE_KEY));
});

test("a configuration error prints ERROR: <message>, then its help", () => {
  const lines = startupFailureLines(
    "Issuer",
    new ConfigurationError("NETWORK_PUBLIC_KEY is not set", NETWORK_PUBLIC_KEY_HELP),
  );

  assert.deepEqual(lines, ["ERROR: NETWORK_PUBLIC_KEY is not set", NETWORK_PUBLIC_KEY_HELP]);
});

test("any other startup failure prints one line naming the cause", () => {
  const bind = new Error("listen EADDRINUSE: address already in use :::8080");

  assert.deepEqual(startupFailureLines("Issuer", bind), [
    "Issuer failed to start: listen EADDRINUSE: address already in use :::8080",
  ]);
  assert.deepEqual(startupFailureLines("Issuer", "boom"), ["Issuer failed to start: boom"]);
});

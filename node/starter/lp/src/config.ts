import { existsSync } from "node:fs";
import { resolve } from "node:path";
import dotenv from "dotenv";
import { publicKeyFromPrivateKey } from "@t-0/usdt-pay-sdk";

/** Everything the starter reads from the environment. */
export interface Config {
  privateKey: string;
  networkPublicKey: string;
  tzeroEndpoint: string;
  port: number;
  /** Derived from privateKey, not read — kept here so a bad key fails as configuration. */
  publicKey: string;
}

/** Configuration is missing or unusable — the process cannot start. */
export class ConfigurationError extends Error {
  constructor(
    message: string,
    readonly help: string,
  ) {
    super(message);
    this.name = "ConfigurationError";
  }
}

const NETWORK_PUBLIC_KEY_HELP = "Ask the t-0 team for the network public key and put it in .env.";

export function loadConfig(): Config {
  // dotenv reads .env from the process working directory, so run the app from the
  // directory holding your .env. Say where we looked — otherwise a .env one directory
  // up looks exactly like a .env that is not filled in.
  const envPath = resolve(".env");
  if (!existsSync(envPath)) {
    console.error(`No .env at ${envPath} — taking configuration from the environment instead`);
  }
  // A variable already set in the environment wins over the same one in .env.
  dotenv.config({ quiet: true });

  // Surrounding whitespace is a copy-paste accident, never part of a key.
  const privateKey = process.env.PROVIDER_PRIVATE_KEY?.trim() ?? "";
  const networkPublicKey = process.env.NETWORK_PUBLIC_KEY?.trim() ?? "";
  // An empty value counts as unset.
  const tzeroEndpoint = process.env.TZERO_ENDPOINT || "https://usdt-pay-api-sandbox.t-0.network";

  if (!privateKey) {
    // Never "copy .env.example to .env" here: a scaffolded project's .env already holds
    // the key the scaffolder generated, its public half is already with the t-0 team,
    // and the private half exists nowhere else. Reaching this line means the .env was
    // not read — nearly always the working directory, since dotenv looks there and
    // nowhere up the tree — or a project that genuinely has none yet.
    throw new ConfigurationError(
      "PROVIDER_PRIVATE_KEY is not set",
      `.env is read from the working directory, and we looked in ${envPath}. Run the app ` +
        "from the directory holding your .env, or set PROVIDER_PRIVATE_KEY in the environment. " +
        "Only a project with no .env at all starts one from .env.example — an existing " +
        ".env holds the key generated for you, and its private half is not recoverable.",
    );
  }

  if (!networkPublicKey) {
    throw new ConfigurationError("NETWORK_PUBLIC_KEY is not set", NETWORK_PUBLIC_KEY_HELP);
  }

  const port = parsePort(process.env.PORT);

  let publicKey: string;
  try {
    publicKey = publicKeyFromPrivateKey(privateKey);
  } catch (error) {
    throw new ConfigurationError(
      `PROVIDER_PRIVATE_KEY is not usable: ${(error as Error).message}`,
      "Any 32 random bytes will do: openssl rand -hex 32.",
    );
  }

  return { privateKey, networkPublicKey, tzeroEndpoint, port, publicKey };
}

/**
 * PORT is ASCII digits only. Number() alone would take "0x50", "1e3" and "+80" as
 * ports, and a value it cannot parse would reach listen() as NaN and bind a random
 * port — which looks like a working server right up until t-0 cannot reach it.
 */
function parsePort(raw: string | undefined): number {
  const value = raw?.trim() ?? "";
  // An empty value counts as unset.
  if (value === "") {
    return 8080;
  }
  const port = /^[0-9]+$/.test(value) ? Number(value) : NaN;
  if (!(port >= 1 && port <= 65535)) {
    throw new ConfigurationError(
      `PORT is not a valid port number: ${raw}`,
      "Set PORT to an integer between 1 and 65535, or leave it unset for 8080.",
    );
  }
  return port;
}

/**
 * The SDK checks NETWORK_PUBLIC_KEY itself, when the server is set up: `createServer`
 * throws, before it returns its promise, an error whose message starts
 * `invalid network public key: `. Pass what it threw through here, so a key that is set
 * but malformed is reported like the checks above. Anything else comes back unchanged.
 */
export function serverSetupError(error: unknown): unknown {
  if (error instanceof Error && error.message.startsWith("invalid network public key: ")) {
    return new ConfigurationError(error.message, NETWORK_PUBLIC_KEY_HELP);
  }
  return error;
}

/**
 * What a failed start prints on stderr, line by line, before the process exits 1: a
 * configuration error as `ERROR: <message>` and its help, anything else as one line
 * naming the cause.
 */
export function startupFailureLines(role: string, error: unknown): string[] {
  if (error instanceof ConfigurationError) {
    return [`ERROR: ${error.message}`, error.help];
  }
  return [`${role} failed to start: ${error instanceof Error ? error.message : String(error)}`];
}

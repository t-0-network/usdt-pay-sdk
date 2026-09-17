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

/** Uncompressed secp256k1 point: 65 bytes as hex, 0x prefix optional. */
const NETWORK_PUBLIC_KEY_PATTERN = /^(0x)?[0-9a-fA-F]{130}$/;

export function loadConfig(): Config {
  // dotenv reads .env from the process working directory, so run the app from the
  // directory holding your .env. Say where we looked — otherwise a .env one directory
  // up looks exactly like a .env that is not filled in.
  const envPath = resolve(".env");
  if (!existsSync(envPath)) {
    console.log(`No .env at ${envPath} — taking configuration from the environment instead`);
  }
  dotenv.config({ quiet: true });

  const privateKey = process.env.PROVIDER_PRIVATE_KEY;
  const networkPublicKey = process.env.NETWORK_PUBLIC_KEY;
  const tzeroEndpoint = process.env.TZERO_ENDPOINT ?? "https://usdt-pay-api-sandbox.t-0.network";

  if (!privateKey?.trim()) {
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

  if (!networkPublicKey?.trim()) {
    throw new ConfigurationError(
      "NETWORK_PUBLIC_KEY is not set",
      "Ask the t-0 team for the network public key and put it in .env.",
    );
  }

  // Checked here so a typo reports as configuration, with somewhere to go for the
  // right value. The signature verifier does reject a malformed key on its own, but
  // not until the first callback arrives.
  if (!NETWORK_PUBLIC_KEY_PATTERN.test(networkPublicKey)) {
    throw new ConfigurationError(
      "NETWORK_PUBLIC_KEY is not a valid uncompressed secp256k1 public key",
      `Expected 130 hex characters (65 bytes), optionally 0x-prefixed; got ${networkPublicKey.length} characters.`,
    );
  }

  // NaN would otherwise reach listen() and bind a random port, which looks like a
  // working server right up until t-0 cannot reach it.
  const port = Number(process.env.PORT ?? "8080");
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new ConfigurationError(
      `PORT is not a valid port number: ${process.env.PORT}`,
      "Set PORT to an integer between 1 and 65535, or leave it unset for 8080.",
    );
  }

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

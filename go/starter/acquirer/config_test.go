package acquirer

import (
	"errors"
	"testing"
)

const testPrivateKey = "0x4c0883a69102937d6231471b5dbb6204fe512961708279f23efb0fecd891d2f2"
const testNetworkKey = "0x04dd549e27ef8acc1c9d2027d7973b1e5d8e014ce5ac36424ef9f7a3ee8a44029dee77d7acbc8e8980453eadc4e60db153403febd147e14c8444de806943524d3e"

func TestLoadConfig_MissingPrivateKey(t *testing.T) {
	// godotenv.Load does not overwrite existing env vars, so setting to empty
	// prevents .env (if present in the working dir) from filling it in.
	t.Setenv("PROVIDER_PRIVATE_KEY", "")
	t.Setenv("NETWORK_PUBLIC_KEY", testNetworkKey)

	_, err := LoadConfig()
	if err == nil {
		t.Fatal("expected error for missing PROVIDER_PRIVATE_KEY")
	}
	var ce *ConfigurationError
	if !errors.As(err, &ce) {
		t.Fatalf("expected ConfigurationError, got %T", err)
	}
	if ce.Msg != "PROVIDER_PRIVATE_KEY is not set" {
		t.Fatalf("unexpected message: %s", ce.Msg)
	}
}

func TestLoadConfig_MissingNetworkKey(t *testing.T) {
	t.Setenv("PROVIDER_PRIVATE_KEY", testPrivateKey)
	t.Setenv("NETWORK_PUBLIC_KEY", "")

	_, err := LoadConfig()
	if err == nil {
		t.Fatal("expected error for missing NETWORK_PUBLIC_KEY")
	}
}

func TestLoadConfig_InvalidNetworkKey(t *testing.T) {
	t.Setenv("PROVIDER_PRIVATE_KEY", testPrivateKey)
	t.Setenv("NETWORK_PUBLIC_KEY", "not-a-key")

	_, err := LoadConfig()
	if err == nil {
		t.Fatal("expected error for invalid NETWORK_PUBLIC_KEY")
	}
}

func TestLoadConfig_InvalidPort(t *testing.T) {
	t.Setenv("PROVIDER_PRIVATE_KEY", testPrivateKey)
	t.Setenv("NETWORK_PUBLIC_KEY", testNetworkKey)
	t.Setenv("PORT", "not-a-port")

	_, err := LoadConfig()
	if err == nil {
		t.Fatal("expected error for invalid PORT")
	}
}

func TestLoadConfig_PortOutOfRange(t *testing.T) {
	t.Setenv("PROVIDER_PRIVATE_KEY", testPrivateKey)
	t.Setenv("NETWORK_PUBLIC_KEY", testNetworkKey)
	t.Setenv("PORT", "99999")

	_, err := LoadConfig()
	if err == nil {
		t.Fatal("expected error for port out of range")
	}
}

func TestLoadConfig_Valid(t *testing.T) {
	t.Setenv("PROVIDER_PRIVATE_KEY", testPrivateKey)
	t.Setenv("NETWORK_PUBLIC_KEY", testNetworkKey)
	t.Setenv("TZERO_ENDPOINT", "https://example.com")
	t.Setenv("PORT", "9090")

	config, err := LoadConfig()
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if config.Port != 9090 {
		t.Errorf("Port = %d, want 9090", config.Port)
	}
	if config.TzeroEndpoint != "https://example.com" {
		t.Errorf("TzeroEndpoint = %s, want https://example.com", config.TzeroEndpoint)
	}
	if config.PublicKey == "" {
		t.Error("PublicKey is empty")
	}
}

func TestLoadConfig_DefaultEndpointAndPort(t *testing.T) {
	t.Setenv("PROVIDER_PRIVATE_KEY", testPrivateKey)
	t.Setenv("NETWORK_PUBLIC_KEY", testNetworkKey)
	t.Setenv("TZERO_ENDPOINT", "")
	t.Setenv("PORT", "")

	config, err := LoadConfig()
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if config.TzeroEndpoint != "https://usdt-pay-api-sandbox.t-0.network" {
		t.Errorf("TzeroEndpoint = %s, want sandbox default", config.TzeroEndpoint)
	}
	if config.Port != 8080 {
		t.Errorf("Port = %d, want 8080", config.Port)
	}
}

package acquirer

import (
	"bytes"
	"errors"
	"io"
	"os"
	"path/filepath"
	"strings"
	"testing"

	usdtpay "github.com/t-0-network/usdt-pay-sdk/go/sdk"
)

const testPrivateKey = "0x4c0883a69102937d6231471b5dbb6204fe512961708279f23efb0fecd891d2f2"
const testNetworkKey = "0x04dd549e27ef8acc1c9d2027d7973b1e5d8e014ce5ac36424ef9f7a3ee8a44029dee77d7acbc8e8980453eadc4e60db153403febd147e14c8444de806943524d3e"

// inEmptyDir runs the test from a fresh directory with no .env, so that the
// project's own .env cannot fill in a value, and returns the path LoadConfig
// looks for .env at.
func inEmptyDir(t *testing.T) string {
	t.Helper()
	t.Chdir(t.TempDir())
	envPath, err := filepath.Abs(".env")
	if err != nil {
		t.Fatal(err)
	}
	return envPath
}

// setEnv sets the variables LoadConfig reads; "<unset>" unsets one.
func setEnv(t *testing.T, privateKey, networkKey, endpoint, port string) {
	t.Helper()
	for name, value := range map[string]string{
		"PROVIDER_PRIVATE_KEY": privateKey,
		"NETWORK_PUBLIC_KEY":   networkKey,
		"TZERO_ENDPOINT":       endpoint,
		"PORT":                 port,
	} {
		t.Setenv(name, value) // restores the variable after the test
		if value == "<unset>" {
			os.Unsetenv(name)
		}
	}
}

// loadConfig runs LoadConfig and also returns what it wrote to stderr.
func loadConfig(t *testing.T) (*Config, string, error) {
	t.Helper()
	r, w, err := os.Pipe()
	if err != nil {
		t.Fatal(err)
	}
	stderr := os.Stderr
	os.Stderr = w
	config, loadErr := LoadConfig()
	os.Stderr = stderr
	w.Close()
	out, err := io.ReadAll(r)
	if err != nil {
		t.Fatal(err)
	}
	return config, string(out), loadErr
}

func requireConfigurationError(t *testing.T, err error, msg, help string) {
	t.Helper()
	var ce *ConfigurationError
	if !errors.As(err, &ce) {
		t.Fatalf("expected ConfigurationError, got %T: %v", err, err)
	}
	if ce.Msg != msg {
		t.Errorf("message:\n got %q\nwant %q", ce.Msg, msg)
	}
	if ce.HelpMessage != help {
		t.Errorf("help:\n got %q\nwant %q", ce.HelpMessage, help)
	}
}

func TestLoadConfig_MissingEnvNotice(t *testing.T) {
	envPath := inEmptyDir(t)
	setEnv(t, testPrivateKey, testNetworkKey, "", "")

	_, stderr, err := loadConfig(t)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	want := "No .env at " + envPath + " — taking configuration from the environment instead\n"
	if stderr != want {
		t.Errorf("stderr:\n got %q\nwant %q", stderr, want)
	}
}

func TestLoadConfig_EnvironmentWinsOverEnvFile(t *testing.T) {
	inEmptyDir(t)
	if err := os.WriteFile(".env", []byte("PORT=1234\n"), 0o600); err != nil {
		t.Fatal(err)
	}
	setEnv(t, testPrivateKey, testNetworkKey, "", "9090")

	config, stderr, err := loadConfig(t)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if stderr != "" {
		t.Errorf("stderr = %q, want nothing with a .env present", stderr)
	}
	if config.Port != 9090 {
		t.Errorf("Port = %d, want 9090 from the environment", config.Port)
	}
}

func TestLoadConfig_MissingPrivateKey(t *testing.T) {
	for _, value := range []string{"", "  \t ", "<unset>"} {
		t.Run(value, func(t *testing.T) {
			envPath := inEmptyDir(t)
			setEnv(t, value, testNetworkKey, "", "")

			_, _, err := loadConfig(t)
			requireConfigurationError(t, err,
				"PROVIDER_PRIVATE_KEY is not set",
				".env is read from the working directory, and we looked in "+envPath+". Run the app from the directory holding your .env, or set PROVIDER_PRIVATE_KEY in the environment. Only a project with no .env at all starts one from .env.example — an existing .env holds the key generated for you, and its private half is not recoverable.")
		})
	}
}

func TestLoadConfig_MalformedPrivateKey(t *testing.T) {
	inEmptyDir(t)
	setEnv(t, "0x1234", testNetworkKey, "", "")
	_, sdkErr := usdtpay.PublicKeyFromPrivateKey("0x1234")
	if sdkErr == nil {
		t.Fatal("the SDK accepted 0x1234")
	}

	_, _, err := loadConfig(t)
	requireConfigurationError(t, err,
		"PROVIDER_PRIVATE_KEY is not usable: "+sdkErr.Error(),
		"Any 32 random bytes will do: openssl rand -hex 32.")
}

func TestLoadConfig_MissingNetworkKey(t *testing.T) {
	for _, value := range []string{"", " \n ", "<unset>"} {
		t.Run(value, func(t *testing.T) {
			inEmptyDir(t)
			setEnv(t, testPrivateKey, value, "", "")

			_, _, err := loadConfig(t)
			requireConfigurationError(t, err,
				"NETWORK_PUBLIC_KEY is not set",
				"Ask the t-0 team for the network public key and put it in .env.")
		})
	}
}

func TestNetworkKeyError_MalformedKey(t *testing.T) {
	_, sdkErr := usdtpay.NewHTTPHandler("04zz")
	if sdkErr == nil {
		t.Fatal("the SDK accepted 04zz")
	}
	if !strings.HasPrefix(sdkErr.Error(), "invalid network public key: ") {
		t.Fatalf("SDK error = %q, want the invalid network public key error", sdkErr)
	}

	err := NetworkKeyError(sdkErr)
	requireConfigurationError(t, err,
		sdkErr.Error(),
		"Ask the t-0 team for the network public key and put it in .env.")

	var out bytes.Buffer
	WriteStartupError(&out, err)
	want := "ERROR: " + sdkErr.Error() + "\nAsk the t-0 team for the network public key and put it in .env.\n"
	if out.String() != want {
		t.Errorf("output:\n got %q\nwant %q", out.String(), want)
	}
}

func TestNetworkKeyError_OtherErrorsUnchanged(t *testing.T) {
	other := errors.New("service must not be null")
	if got := NetworkKeyError(other); got != other {
		t.Errorf("NetworkKeyError(%v) = %v, want it unchanged", other, got)
	}
	if got := NetworkKeyError(nil); got != nil {
		t.Errorf("NetworkKeyError(nil) = %v, want nil", got)
	}
}

func TestLoadConfig_InvalidPort(t *testing.T) {
	for _, value := range []string{
		"not-a-port", "99999", "65536", "0", "00", "+8080", "-1", "0x1F90", "8080.0",
		"8e3", "80_80", "٨٠٨٠", "8080x", " 99999 ", "18446744073709551617",
	} {
		t.Run(value, func(t *testing.T) {
			inEmptyDir(t)
			setEnv(t, testPrivateKey, testNetworkKey, "", value)

			_, _, err := loadConfig(t)
			requireConfigurationError(t, err,
				"PORT is not a valid port number: "+value,
				"Set PORT to an integer between 1 and 65535, or leave it unset for 8080.")
		})
	}
}

func TestLoadConfig_Port(t *testing.T) {
	tests := []struct {
		value string
		want  int
	}{
		{"<unset>", 8080},
		{"", 8080},
		{"  ", 8080},
		{" 9090\n", 9090},
		{"1", 1},
		{"65535", 65535},
		{"08080", 8080},
	}
	for _, tt := range tests {
		t.Run(tt.value, func(t *testing.T) {
			inEmptyDir(t)
			setEnv(t, testPrivateKey, testNetworkKey, "", tt.value)

			config, _, err := loadConfig(t)
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if config.Port != tt.want {
				t.Errorf("Port = %d, want %d", config.Port, tt.want)
			}
		})
	}
}

func TestLoadConfig_Valid(t *testing.T) {
	inEmptyDir(t)
	setEnv(t, " "+testPrivateKey+"\n", "\t"+testNetworkKey+" ", "https://example.com", "9090")

	config, _, err := loadConfig(t)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if config.PrivateKey != testPrivateKey {
		t.Errorf("PrivateKey = %q, want it trimmed", config.PrivateKey)
	}
	if config.NetworkPublicKey != testNetworkKey {
		t.Errorf("NetworkPublicKey = %q, want it trimmed", config.NetworkPublicKey)
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

func TestLoadConfig_DefaultEndpoint(t *testing.T) {
	for _, value := range []string{"", "<unset>"} {
		t.Run(value, func(t *testing.T) {
			inEmptyDir(t)
			setEnv(t, testPrivateKey, testNetworkKey, value, "")

			config, _, err := loadConfig(t)
			if err != nil {
				t.Fatalf("unexpected error: %v", err)
			}
			if config.TzeroEndpoint != "https://usdt-pay-api-sandbox.t-0.network" {
				t.Errorf("TzeroEndpoint = %s, want sandbox default", config.TzeroEndpoint)
			}
		})
	}
}

func TestWriteStartupError(t *testing.T) {
	t.Run("configuration error", func(t *testing.T) {
		var out bytes.Buffer
		WriteStartupError(&out, &ConfigurationError{Msg: "NETWORK_PUBLIC_KEY is not set", HelpMessage: "Ask the t-0 team for the network public key and put it in .env."})
		want := "ERROR: NETWORK_PUBLIC_KEY is not set\nAsk the t-0 team for the network public key and put it in .env.\n"
		if out.String() != want {
			t.Errorf("output:\n got %q\nwant %q", out.String(), want)
		}
	})
	t.Run("other failure", func(t *testing.T) {
		var out bytes.Buffer
		WriteStartupError(&out, errors.New("listen tcp :8080: bind: address already in use"))
		want := "Acquirer failed to start: listen tcp :8080: bind: address already in use\n"
		if out.String() != want {
			t.Errorf("output:\n got %q\nwant %q", out.String(), want)
		}
	})
}

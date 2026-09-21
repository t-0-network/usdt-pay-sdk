package acquirer

import (
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strconv"

	"github.com/joho/godotenv"
	usdtpay "github.com/t-0-network/usdt-pay-sdk/go/sdk"
)

var networkPublicKeyPattern = regexp.MustCompile(`^(0x)?[0-9a-fA-F]{130}$`)

type Config struct {
	PrivateKey       string
	NetworkPublicKey string
	TzeroEndpoint    string
	Port             int
	PublicKey        string
}

type ConfigurationError struct {
	Msg         string
	HelpMessage string
}

func (e *ConfigurationError) Error() string { return e.Msg }

func LoadConfig() (*Config, error) {
	envPath, _ := filepath.Abs(".env")
	if _, err := os.Stat(envPath); err != nil {
		fmt.Fprintf(os.Stderr, "No .env at %s — taking configuration from the environment instead\n", envPath)
	}
	_ = godotenv.Load(".env")

	privateKey := os.Getenv("PROVIDER_PRIVATE_KEY")
	networkPublicKey := os.Getenv("NETWORK_PUBLIC_KEY")
	tzeroEndpoint := os.Getenv("TZERO_ENDPOINT")
	if tzeroEndpoint == "" {
		tzeroEndpoint = "https://usdt-pay-api-sandbox.t-0.network"
	}
	portStr := os.Getenv("PORT")
	if portStr == "" {
		portStr = "8080"
	}

	if privateKey == "" {
		return nil, &ConfigurationError{
			Msg: "PROVIDER_PRIVATE_KEY is not set",
			HelpMessage: "Add it to " + envPath + ", editing that file in place. " +
				"If there is no .env here, run this from your project directory, whose " +
				".env holds the key generated for you.",
		}
	}

	publicKey, err := usdtpay.PublicKeyFromPrivateKey(privateKey)
	if err != nil {
		return nil, &ConfigurationError{
			Msg:         fmt.Sprintf("PROVIDER_PRIVATE_KEY is not usable: %v", err),
			HelpMessage: "Any 32 random bytes will do: openssl rand -hex 32.",
		}
	}

	if networkPublicKey == "" {
		return nil, &ConfigurationError{
			Msg:         "NETWORK_PUBLIC_KEY is not set",
			HelpMessage: "Ask the t-0 team for the network public key and put it in .env.",
		}
	}

	if !networkPublicKeyPattern.MatchString(networkPublicKey) {
		return nil, &ConfigurationError{
			Msg: "NETWORK_PUBLIC_KEY is not a valid uncompressed secp256k1 public key",
			HelpMessage: fmt.Sprintf(
				"Expected 130 hex characters (65 bytes), optionally 0x-prefixed; got %d characters.",
				len(networkPublicKey)),
		}
	}

	port, err := strconv.Atoi(portStr)
	if err != nil || port < 1 || port > 65535 {
		return nil, &ConfigurationError{
			Msg:         fmt.Sprintf("PORT is not a valid port number: %s", portStr),
			HelpMessage: "Set PORT to an integer between 1 and 65535, or leave it unset for 8080.",
		}
	}

	return &Config{
		PrivateKey:       privateKey,
		NetworkPublicKey: networkPublicKey,
		TzeroEndpoint:    tzeroEndpoint,
		Port:             port,
		PublicKey:        publicKey,
	}, nil
}

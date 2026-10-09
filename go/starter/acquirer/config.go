package acquirer

import (
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"

	"github.com/joho/godotenv"
	usdtpay "github.com/t-0-network/usdt-pay-sdk/go/sdk"
)

type Config struct {
	PrivateKey       string
	NetworkPublicKey string
	TzeroEndpoint    string
	Port             int
	PublicKey        string
}

// ConfigurationError is a value in .env or the environment that the acquirer
// cannot start with. Msg says what is wrong, HelpMessage what to do about it.
type ConfigurationError struct {
	Msg         string
	HelpMessage string
}

func (e *ConfigurationError) Error() string { return e.Msg }

const networkPublicKeyHelp = "Ask the t-0 team for the network public key and put it in .env."

// The SDK's message for a malformed NETWORK_PUBLIC_KEY, which it refuses when
// the callback handler is built.
const invalidNetworkPublicKeyPrefix = "invalid network public key: "

// ASCII digits only: no sign, no 0x, no exponent, no _.
var portPattern = regexp.MustCompile(`^[0-9]+$`)

func LoadConfig() (*Config, error) {
	envPath, _ := filepath.Abs(".env")
	if _, err := os.Stat(envPath); errors.Is(err, fs.ErrNotExist) {
		fmt.Fprintln(os.Stderr, "No .env at "+envPath+" — taking configuration from the environment instead")
	}
	// The environment wins: godotenv never overwrites a variable that is already
	// set, even to an empty value.
	_ = godotenv.Load(".env")

	privateKey := strings.TrimSpace(os.Getenv("PROVIDER_PRIVATE_KEY"))
	networkPublicKey := strings.TrimSpace(os.Getenv("NETWORK_PUBLIC_KEY"))
	// An empty value counts as unset.
	tzeroEndpoint := os.Getenv("TZERO_ENDPOINT")
	if tzeroEndpoint == "" {
		tzeroEndpoint = "https://usdt-pay-api-sandbox.t-0.network"
	}
	rawPort := os.Getenv("PORT")

	if privateKey == "" {
		return nil, &ConfigurationError{
			Msg: "PROVIDER_PRIVATE_KEY is not set",
			HelpMessage: ".env is read from the working directory, and we looked in " + envPath + ". " +
				"Run the app from the directory holding your .env, or set PROVIDER_PRIVATE_KEY in the environment. " +
				"Only a project with no .env at all starts one from .env.example — an existing .env holds " +
				"the key generated for you, and its private half is not recoverable.",
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
			HelpMessage: networkPublicKeyHelp,
		}
	}

	port, ok := parsePort(rawPort)
	if !ok {
		return nil, &ConfigurationError{
			Msg:         fmt.Sprintf("PORT is not a valid port number: %s", rawPort),
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

// parsePort reads PORT. Unset, empty or blank gives 8080.
func parsePort(raw string) (int, bool) {
	s := strings.TrimSpace(raw)
	if s == "" {
		return 8080, true
	}
	if !portPattern.MatchString(s) {
		return 0, false
	}
	port, err := strconv.Atoi(s)
	if err != nil || port < 1 || port > 65535 {
		return 0, false
	}
	return port, true
}

// NetworkKeyError turns the SDK's refusal of NETWORK_PUBLIC_KEY, which comes
// from usdtpay.NewHTTPHandler, into the configuration error it is. Any other
// error is returned unchanged.
func NetworkKeyError(err error) error {
	if err != nil && strings.HasPrefix(err.Error(), invalidNetworkPublicKeyPrefix) {
		return &ConfigurationError{Msg: err.Error(), HelpMessage: networkPublicKeyHelp}
	}
	return err
}

// WriteStartupError prints why the acquirer did not start: a configuration
// error as "ERROR: <message>" and its help on the next line, anything else as
// one "Acquirer failed to start: <cause>" line.
func WriteStartupError(w io.Writer, err error) {
	var ce *ConfigurationError
	if errors.As(err, &ce) {
		fmt.Fprintln(w, "ERROR: "+ce.Msg)
		fmt.Fprintln(w, ce.HelpMessage)
		return
	}
	fmt.Fprintln(w, "Acquirer failed to start: "+err.Error())
}

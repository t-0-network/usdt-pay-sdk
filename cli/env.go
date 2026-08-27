package main

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

func writeEnvFile(projectDir string, kp KeyPair) error {
	envExample := filepath.Join(projectDir, ".env.example")
	data, err := os.ReadFile(envExample)
	if err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return fmt.Errorf("reading .env.example: %w", err)
	}

	content := string(data)

	// Replace private key placeholder — templates use various patterns:
	//   PRIVATE_KEY=your_private_key_here            (usdt-pay-sdk starters)
	//   PRIVATE_KEY=                                 (usdt-pay-sdk starters)
	//   PROVIDER_PRIVATE_KEY=your_private_key_here  (provider-sdk starters)
	//   PROVIDER_PRIVATE_KEY=                        (provider-sdk starters)
	for _, pattern := range []string{
		"PRIVATE_KEY=your_private_key_here",
		"PROVIDER_PRIVATE_KEY=your_private_key_here",
		"PRIVATE_KEY=",
		"PROVIDER_PRIVATE_KEY=",
	} {
		if strings.Contains(content, pattern) {
			keyName := pattern[:strings.Index(pattern, "=")+1]
			content = strings.Replace(content, pattern, keyName+kp.PrivateKey, 1)
			break
		}
	}

	// Replace public key placeholder if present
	if strings.Contains(content, "# your_public_key_here") {
		content = strings.Replace(content, "# your_public_key_here", "# "+kp.PublicKey, 1)
	}

	envPath := filepath.Join(projectDir, ".env")
	if err := os.WriteFile(envPath, []byte(content), 0600); err != nil {
		return fmt.Errorf("writing .env: %w", err)
	}
	return nil
}

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
	//   PROVIDER_PRIVATE_KEY=your_private_key_here  (Go, Python, Node)
	//   PROVIDER_PRIVATE_KEY=                        (Java, C#)
	for _, pattern := range []string{
		"PROVIDER_PRIVATE_KEY=your_private_key_here",
		"PROVIDER_PRIVATE_KEY=",
	} {
		if strings.Contains(content, pattern) {
			content = strings.Replace(content, pattern, "PROVIDER_PRIVATE_KEY="+kp.PrivateKey, 1)
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

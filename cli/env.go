package main

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

// Variables a template may use for the provider's private key, most specific
// first. Only an active (uncommented) assignment counts, and the whole line is
// replaced — whatever placeholder value the template carries.
var privateKeyVars = []string{"PROVIDER_PRIVATE_KEY", "PRIVATE_KEY"}

const publicKeyMarker = "# your_public_key_here"

func writeEnvFile(projectDir string, kp KeyPair) error {
	envExample := filepath.Join(projectDir, ".env.example")
	data, err := os.ReadFile(envExample)
	if err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return fmt.Errorf("reading .env.example: %w", err)
	}

	lines := strings.Split(string(data), "\n")

	keyIdx := -1
	for _, name := range privateKeyVars {
		for i, line := range lines {
			if k, _, ok := strings.Cut(line, "="); ok && strings.TrimSpace(k) == name {
				keyIdx = i
				lines[i] = name + "=" + kp.PrivateKey
				break
			}
		}
		if keyIdx >= 0 {
			break
		}
	}

	// Record the matching public key next to the private key, so it can be
	// found later without re-running init: in the template's marker when it
	// has one, otherwise on a comment line right under the key.
	markerIdx := -1
	for i, line := range lines {
		if strings.TrimSpace(line) == publicKeyMarker {
			markerIdx = i
			break
		}
	}
	switch {
	case markerIdx >= 0:
		lines[markerIdx] = "# " + kp.PublicKey
	case keyIdx >= 0:
		comment := "# Public key for the line above (share it with t-0): " + kp.PublicKey
		lines = append(lines[:keyIdx+1], append([]string{comment}, lines[keyIdx+1:]...)...)
	}

	envPath := filepath.Join(projectDir, ".env")
	if err := os.WriteFile(envPath, []byte(strings.Join(lines, "\n")), 0600); err != nil {
		return fmt.Errorf("writing .env: %w", err)
	}
	// WriteFile keeps the mode of a file that already exists; the key must not
	// be readable by others whatever the template shipped.
	if err := os.Chmod(envPath, 0600); err != nil {
		return fmt.Errorf("securing .env: %w", err)
	}
	return nil
}

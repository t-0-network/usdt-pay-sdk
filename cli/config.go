package main

import (
	"fmt"
	"os"
	"path/filepath"
)

var Config = CLIConfig{
	ProductName:  "usdt-pay",
	Command:      "usdt-pay init",
	RoleRequired: true,
	DefaultRole:  "",
	Languages:    []string{"java", "node"},
	PostScaffold: usdtPayPostScaffold,
}

func usdtPayPostScaffold(opts ScaffoldOpts) error {
	if opts.Lang != "java" || opts.Version == "" || opts.Version == "dev" {
		return nil
	}
	propsPath := filepath.Join(opts.ProjectDir, "gradle.properties")
	if _, err := os.Stat(propsPath); !os.IsNotExist(err) {
		return nil
	}
	content := "usdtPaySdkVersion=" + opts.Version + "\n"
	if err := os.WriteFile(propsPath, []byte(content), 0666); err != nil {
		return fmt.Errorf("writing gradle.properties: %w", err)
	}
	fmt.Printf("%s SDK version pinned to %s\n", color(green, "[OK]"), opts.Version)
	return nil
}

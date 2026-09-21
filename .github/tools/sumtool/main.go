// Command sumtool emits a file:// GOPROXY layout for the Go SDK module at a
// version that has no git tag yet, so the starter template's go.sum can be
// generated in the same release commit that bumps its require line.
//
// A module's h1: hash is a function of its source tree, not of the tag, so the
// checksums recorded against this layout are exactly the ones proxy.golang.org
// serves once publish.yaml pushes go/sdk/vX.Y.Z.
//
// Usage: sumtool <module-dir> <vX.Y.Z> <proxy-dir>
package main

import (
	"fmt"
	"log"
	"os"
	"path/filepath"

	"golang.org/x/mod/module"
	"golang.org/x/mod/zip"
)

const modPath = "github.com/t-0-network/usdt-pay-sdk/go/sdk"

func main() {
	if len(os.Args) != 4 {
		log.Fatalf("usage: %s <module-dir> <vX.Y.Z> <proxy-dir>", os.Args[0])
	}
	srcDir, version, proxyDir := os.Args[1], os.Args[2], os.Args[3]
	if err := writeProxy(srcDir, version, proxyDir); err != nil {
		log.Fatal(err)
	}
	fmt.Printf("wrote %s@%s to %s\n", modPath, version, proxyDir)
}

func writeProxy(srcDir, version, proxyDir string) error {
	// cmd/go synthesizes the repo-root LICENSE into the zip when the module
	// directory has none; x/mod/zip does not. Without go/sdk/LICENSE the locally
	// computed hash would silently diverge from the proxy's.
	if _, err := os.Stat(filepath.Join(srcDir, "LICENSE")); err != nil {
		return fmt.Errorf("%s/LICENSE is missing: the computed hash would not match proxy.golang.org", srcDir)
	}

	dir := filepath.Join(proxyDir, filepath.FromSlash(modPath), "@v")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return err
	}

	goMod, err := os.ReadFile(filepath.Join(srcDir, "go.mod"))
	if err != nil {
		return err
	}
	write := func(name string, data []byte) error {
		return os.WriteFile(filepath.Join(dir, name), data, 0o644)
	}
	if err := write("list", []byte(version+"\n")); err != nil {
		return err
	}
	if err := write(version+".info", []byte(fmt.Sprintf("{%q:%q}\n", "Version", version))); err != nil {
		return err
	}
	if err := write(version+".mod", goMod); err != nil {
		return err
	}

	f, err := os.Create(filepath.Join(dir, version+".zip"))
	if err != nil {
		return err
	}
	if err := zip.CreateFromDir(f, module.Version{Path: modPath, Version: version}, srcDir); err != nil {
		_ = f.Close()
		return err
	}
	return f.Close()
}

package main

import (
	"bytes"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"

	"golang.org/x/mod/sumdb/dirhash"
)

const testVersion = "v1.2.3"

func TestLicenseMissing(t *testing.T) {
	src := t.TempDir()
	if err := os.WriteFile(filepath.Join(src, "go.mod"), []byte("module x\n\ngo 1.25.0\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	err := writeProxy(src, testVersion, t.TempDir())
	if err == nil {
		t.Fatal("expected error when LICENSE is missing")
	}
	if !strings.Contains(err.Error(), "LICENSE is missing") {
		t.Fatalf("error = %v, want LICENSE is missing", err)
	}
}

func TestWriteProxyLayout(t *testing.T) {
	proxy := t.TempDir()
	if err := writeProxy(filepath.Join("testdata", "module"), testVersion, proxy); err != nil {
		t.Fatal(err)
	}
	dir := filepath.Join(proxy, filepath.FromSlash(modPath), "@v")
	for _, name := range []string{"list", testVersion + ".info", testVersion + ".mod", testVersion + ".zip"} {
		if _, err := os.Stat(filepath.Join(dir, name)); err != nil {
			t.Errorf("missing %s: %v", name, err)
		}
	}
	list, err := os.ReadFile(filepath.Join(dir, "list"))
	if err != nil {
		t.Fatal(err)
	}
	if got := string(list); got != testVersion+"\n" {
		t.Fatalf("list = %q, want %q", got, testVersion+"\n")
	}
}

func TestTidyRecordsHash(t *testing.T) {
	if _, err := exec.LookPath("go"); err != nil {
		t.Skip("go toolchain not on PATH")
	}

	proxy := t.TempDir()
	src := filepath.Join("testdata", "module")
	if err := writeProxy(src, testVersion, proxy); err != nil {
		t.Fatal(err)
	}

	want, err := dirhash.HashZip(filepath.Join(proxy, filepath.FromSlash(modPath), "@v", testVersion+".zip"), dirhash.Hash1)
	if err != nil {
		t.Fatal(err)
	}

	work := t.TempDir()
	for _, name := range []string{"go.mod", "main.go"} {
		data, err := os.ReadFile(filepath.Join("testdata", "consumer", name))
		if err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(filepath.Join(work, name), data, 0o644); err != nil {
			t.Fatal(err)
		}
	}

	cmd := exec.Command("go", "mod", "tidy")
	cmd.Dir = work
	cmd.Env = append(os.Environ(),
		"GOPROXY=file://"+proxy,
		"GONOSUMDB=github.com/t-0-network",
		"GOTOOLCHAIN=local",
	)
	out, err := cmd.CombinedOutput()
	if err != nil {
		t.Fatalf("go mod tidy: %v\n%s", err, out)
	}

	sum, err := os.ReadFile(filepath.Join(work, "go.sum"))
	if err != nil {
		t.Fatal(err)
	}
	line := modPath + " " + testVersion + " " + want
	if !bytes.Contains(sum, []byte(line)) {
		t.Fatalf("go.sum missing %s\n%s", line, sum)
	}

	build := exec.Command("go", "build", "-mod=readonly", ".")
	build.Dir = work
	build.Env = cmd.Env
	if out, err := build.CombinedOutput(); err != nil {
		t.Fatalf("go build -mod=readonly: %v\n%s", err, out)
	}
}

func TestZipRealSDKTree(t *testing.T) {
	sdk := filepath.Join("..", "..", "..", "go", "sdk")
	if _, err := os.Stat(filepath.Join(sdk, "LICENSE")); err != nil {
		t.Skip("not a repository checkout")
	}
	proxy := t.TempDir()
	if err := writeProxy(sdk, "v0.0.0", proxy); err != nil {
		t.Fatal(err)
	}
	h, err := dirhash.HashZip(filepath.Join(proxy, filepath.FromSlash(modPath), "@v", "v0.0.0.zip"), dirhash.Hash1)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(h, "h1:") {
		t.Fatalf("hash = %q, want h1: prefix", h)
	}
}

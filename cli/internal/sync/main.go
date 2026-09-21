// Sync tool: copies templates from their source directories into cli/internal/embed/
// for go:embed. Invoked via go:generate.
//
// Usage: go run ./internal/sync <lang1> [lang2...] [lang=path override...]
//
// Convention: templates live at <lang>/starter/template/ relative to repo root.
// Keys may be "lang" or "lang/role" (e.g. go/acquirer=go/starter/acquirer).
//
// Go templates require a go.mod at their root; the module directive must be
// parsable so the scaffolder can read it at scaffold time. Files ending in
// .go, go.mod, and go.sum are renamed to .tmpl to prevent go:embed from
// treating them as source.
//
// Other handling:
//   - Filters out build artifacts (node_modules, dist, __pycache__, build, .venv, etc.)
//   - Skips .git directories, OS metadata files, and .env / .env.* except .env.example
package main

import (
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"strings"

	"github.com/t-0-network/provider-sdk/cli/internal/gomod"
)

var skipDirs = map[string]bool{
	"node_modules":  true,
	"dist":          true,
	"build":         true,
	"__pycache__":   true,
	".venv":         true,
	".git":          true,
	".gradle":       true,
	".idea":         true,
	".vs":           true,
	".DS_Store":     true,
	"obj":           true,
	"bin":           true,
	".pytest_cache": true,
	".ruff_cache":   true,
}

var skipFiles = map[string]bool{
	".DS_Store": true,
	"Thumbs.db": true,
}

func main() {
	if len(os.Args) < 2 {
		fatalf("usage: sync <lang1> [lang2...] [lang=path ...]")
	}

	repoRoot, err := findRepoRoot()
	if err != nil {
		fatalf("finding repo root: %v", err)
	}

	embedDir := filepath.Join(repoRoot, "cli", "internal", "embed")

	// Parse args: plain "go" uses convention, "python=some/path" overrides
	overrides := map[string]string{}
	var langs []string
	for _, arg := range os.Args[1:] {
		if k, v, ok := strings.Cut(arg, "="); ok {
			overrides[k] = v
			langs = append(langs, k)
		} else {
			langs = append(langs, arg)
		}
	}

	for i, a := range langs {
		for _, b := range langs[i+1:] {
			ca, cb := filepath.ToSlash(filepath.Clean(a))+"/", filepath.ToSlash(filepath.Clean(b))+"/"
			if strings.HasPrefix(ca, cb) || strings.HasPrefix(cb, ca) {
				fatalf("overlapping keys: %s and %s would share embed directory", a, b)
			}
		}
	}

	for _, lang := range langs {
		src := lang + "/starter/template"
		if override, ok := overrides[lang]; ok {
			src = override
		}

		srcDir := filepath.Join(repoRoot, src)
		destDir := filepath.Join(embedDir, lang)

		if _, err := os.Stat(srcDir); os.IsNotExist(err) {
			fmt.Printf("skipping %s: %s not found\n", lang, src)
			continue
		}

		isGo := lang == "go" || strings.HasPrefix(lang, "go/")
		if isGo {
			data, _ := os.ReadFile(filepath.Join(srcDir, "go.mod"))
			if gomod.ModulePath(data) == "" {
				fatalf("%s: no module directive in %s/go.mod", lang, src)
			}
		}

		fmt.Printf("syncing %s: %s → %s\n", lang, src, destDir)

		os.RemoveAll(destDir)
		if err := os.MkdirAll(destDir, 0777); err != nil {
			fatalf("creating %s: %v", destDir, err)
		}

		if err := copyTree(srcDir, destDir, isGo); err != nil {
			fatalf("copying %s: %v", lang, err)
		}
	}

	fmt.Println("done")
}

// copyTree copies srcDir to destDir. When isGo is true, .go/.mod/.sum files are
// renamed to .tmpl so go:embed treats them as data, not source.
func copyTree(srcDir, destDir string, isGo bool) error {
	return filepath.WalkDir(srcDir, func(src string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}

		rel, err := filepath.Rel(srcDir, src)
		if err != nil {
			return err
		}
		if rel == "." {
			return nil
		}

		base := filepath.Base(rel)

		if d.IsDir() {
			if skipDirs[base] {
				return filepath.SkipDir
			}
			return os.MkdirAll(filepath.Join(destDir, rel), 0777)
		}

		if skipFiles[base] {
			return nil
		}
		// A developer's local .env (with a real private key) must never become
		// part of the template; only the example ships.
		if base == ".env" || (strings.HasPrefix(base, ".env.") && base != ".env.example") {
			return nil
		}

		destRel := rel
		if isGo && (strings.HasSuffix(base, ".go") || base == "go.mod" || base == "go.sum") {
			destRel += ".tmpl"
		}

		destPath := filepath.Join(destDir, destRel)
		if err := os.MkdirAll(filepath.Dir(destPath), 0777); err != nil {
			return err
		}

		data, err := os.ReadFile(src)
		if err != nil {
			return fmt.Errorf("reading %s: %w", src, err)
		}

		info, err := d.Info()
		if err != nil {
			return err
		}
		mode := info.Mode().Perm()
		if mode&0111 == 0 {
			mode = 0666
		}

		return os.WriteFile(destPath, data, mode)
	})
}

func findRepoRoot() (string, error) {
	dir, err := os.Getwd()
	if err != nil {
		return "", err
	}
	for {
		if _, err := os.Stat(filepath.Join(dir, ".git")); err == nil {
			return dir, nil
		}
		parent := filepath.Dir(dir)
		if parent == dir {
			return "", fmt.Errorf("no .git found above %s", dir)
		}
		dir = parent
	}
}

func fatalf(format string, args ...any) {
	fmt.Fprintf(os.Stderr, "sync: "+format+"\n", args...)
	os.Exit(1)
}

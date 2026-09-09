package main

import (
	"bytes"
	"encoding/hex"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"slices"
	"sort"
	"strings"
	"testing"

	"github.com/decred/dcrd/dcrec/secp256k1/v4"
)

// starterTargets lists every lang/role pair with an embedded starter template
// and requires that set to equal the starters in the source tree
// (<lang>/starter/<role>/), so a starter that drops out of generate.go fails
// here instead of silently losing coverage. Languages are discovered from the
// tree, not taken from Config.Languages: a new <lang>/starter/ that is wired
// into neither generate.go nor config.go must fail too.
func starterTargets(t *testing.T) [][2]string {
	t.Helper()
	var langs []string
	entries, err := os.ReadDir("..")
	if err != nil {
		t.Fatal(err)
	}
	for _, e := range entries {
		if info, err := os.Stat(filepath.Join("..", e.Name(), "starter")); err == nil && info.IsDir() {
			langs = append(langs, e.Name())
		}
	}
	sort.Strings(langs)
	configured := slices.Clone(Config.Languages)
	sort.Strings(configured)
	if !slices.Equal(langs, configured) {
		t.Fatalf("languages with a starter in the tree %v, Config.Languages %v — wire the missing one into cli/config.go", langs, configured)
	}

	var targets [][2]string
	for _, lang := range langs {
		roles, err := listRoles(lang)
		if err != nil {
			t.Fatalf("listRoles(%s): %v — run 'go generate ./...' first", lang, err)
		}
		sort.Strings(roles)

		var inTree []string
		entries, err := os.ReadDir(filepath.Join("..", lang, "starter"))
		if err != nil {
			t.Fatalf("reading ../%s/starter: %v", lang, err)
		}
		for _, e := range entries {
			if e.IsDir() {
				inTree = append(inTree, e.Name())
			}
		}
		sort.Strings(inTree)
		if !slices.Equal(roles, inTree) {
			t.Fatalf("%s: embedded starters %v, starters in ../%s/starter %v — wire the missing one into cli/generate.go", lang, roles, lang, inTree)
		}

		for _, role := range roles {
			targets = append(targets, [2]string{lang, role})
		}
	}
	if len(targets) == 0 {
		t.Fatal("no starter templates embedded")
	}
	return targets
}

// entryFiles are what a scaffolded project of each language must contain for
// "it compiles" in CI to mean anything: an empty tree compiles too.
var entryFiles = map[string][]string{
	"java":   {"build.gradle.kts", "settings.gradle.kts", "gradlew", "src/main/java"},
	"node":   {"package.json", "tsconfig.json", "src/index.ts"},
	"python": {"pyproject.toml", "src/acquirer/main.py"},
}

func requireEntryFiles(t *testing.T, lang, projectDir string) {
	t.Helper()
	files, ok := entryFiles[lang]
	if !ok {
		t.Fatalf("no entry files listed for lang=%s — add them to entryFiles", lang)
	}
	for _, f := range files {
		if _, err := os.Stat(filepath.Join(projectDir, filepath.FromSlash(f))); err != nil {
			t.Errorf("%s missing from the scaffolded project: %v", f, err)
		}
	}
	if lang == "java" {
		sources := 0
		filepath.WalkDir(filepath.Join(projectDir, "src", "main", "java"), func(p string, d fs.DirEntry, err error) error {
			if err == nil && !d.IsDir() && strings.HasSuffix(p, ".java") {
				sources++
			}
			return nil
		})
		if sources == 0 {
			t.Error("no .java sources under src/main/java — the scaffold would compile to nothing")
		}
	}
}

// captureStdout runs fn with os.Stdout redirected and returns what it printed.
// run() reports the generated public key to the user on stdout; the tests
// check that report against the key actually written to .env.
func captureStdout(t *testing.T, fn func() error) (string, error) {
	t.Helper()
	orig := os.Stdout
	r, w, err := os.Pipe()
	if err != nil {
		t.Fatal(err)
	}
	os.Stdout = w
	var buf bytes.Buffer
	done := make(chan struct{})
	go func() {
		io.Copy(&buf, r)
		close(done)
	}()
	fnErr := fn()
	w.Close()
	os.Stdout = orig
	<-done
	return buf.String(), fnErr
}

func parseDotenv(t *testing.T, p string) map[string]string {
	t.Helper()
	data, err := os.ReadFile(p)
	if err != nil {
		t.Fatalf("reading %s: %v", p, err)
	}
	vars := map[string]string{}
	for _, line := range strings.Split(string(data), "\n") {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		if k, v, ok := strings.Cut(line, "="); ok {
			vars[k] = v
		}
	}
	return vars
}

var (
	privateKeyRe = regexp.MustCompile(`^0x[0-9a-f]{64}$`)
	// Uncompressed secp256k1 public key: 0x04 + X + Y, as run() prints it.
	printedPublicKeyRe = regexp.MustCompile(`0x04[0-9a-f]{128}`)
)

// instantiate runs the CLI end to end for one starter into a fresh directory
// and returns the project dir and everything run() printed.
func instantiate(t *testing.T, lang, role string) (string, string) {
	t.Helper()
	projectDir := filepath.Join(t.TempDir(), "test-project")
	out, err := captureStdout(t, func() error {
		return run(ScaffoldOpts{
			Lang:        lang,
			Role:        role,
			ProjectName: "test-project",
			ProjectDir:  projectDir,
			Version:     "dev",
		})
	})
	if err != nil {
		t.Fatalf("run(%s/%s): %v\n%s", lang, role, err, out)
	}
	return projectDir, out
}

func TestStarters_HaveDockerfiles(t *testing.T) {
	for _, tgt := range starterTargets(t) {
		lang, role := tgt[0], tgt[1]
		starterDir := filepath.Join("..", lang, "starter", role)
		for _, name := range []string{"Dockerfile", ".dockerignore"} {
			if _, err := os.Stat(filepath.Join(starterDir, name)); err != nil {
				t.Errorf("%s/%s: %s missing from the starter — a scaffolded project needs it", lang, role, name)
			}
		}
	}
}

func TestRun_InstantiatesEveryStarter(t *testing.T) {
	for _, tgt := range starterTargets(t) {
		lang, role := tgt[0], tgt[1]
		t.Run(lang+"/"+role, func(t *testing.T) {
			projectDir, _ := instantiate(t, lang, role)

			for _, name := range []string{".gitignore", ".env"} {
				if _, err := os.Stat(filepath.Join(projectDir, name)); err != nil {
					t.Errorf("%s missing after run(): %v", name, err)
				}
			}
			// The template ships .gitignore as dot-gitignore (a real .gitignore
			// would be dropped by go:embed); the scaffolder renames it.
			if _, err := os.Stat(filepath.Join(projectDir, "dot-gitignore")); err == nil {
				t.Error("dot-gitignore still present — the template rename did not happen")
			}
			requireEntryFiles(t, lang, projectDir)

			for _, name := range []string{"Dockerfile", ".dockerignore"} {
				if _, err := os.Stat(filepath.Join(projectDir, name)); err != nil {
					t.Errorf("%s missing from the scaffolded project: %v", name, err)
				}
			}
		})
	}
}

// Every instantiated project must carry a fresh, valid secp256k1 private key
// in .env, and the public key the CLI reports to the user must be the one that
// key derives — the user hands that public key to t-0.
func TestRun_WritesFreshPrivateKey(t *testing.T) {
	seenPriv := map[string]string{}
	seenPub := map[string]string{}

	for _, tgt := range starterTargets(t) {
		lang, role := tgt[0], tgt[1]
		// Twice per starter: uniqueness must hold across repeated runs of the
		// same template, not only across templates.
		for i := 0; i < 2; i++ {
			name := lang + "/" + role
			t.Run(name, func(t *testing.T) {
				projectDir, printed := instantiate(t, lang, role)
				envPath := filepath.Join(projectDir, ".env")

				env := parseDotenv(t, envPath)
				priv, ok := env["PROVIDER_PRIVATE_KEY"]
				if !ok {
					t.Fatalf(".env has no PROVIDER_PRIVATE_KEY line:\n%s", env)
				}
				if !privateKeyRe.MatchString(priv) {
					t.Fatalf("PROVIDER_PRIVATE_KEY = %q, want 0x + 64 lowercase hex", priv)
				}

				raw, err := hex.DecodeString(priv[2:])
				if err != nil {
					t.Fatal(err)
				}
				var scalar secp256k1.ModNScalar
				if overflow := scalar.SetByteSlice(raw); overflow {
					t.Fatalf("PROVIDER_PRIVATE_KEY %s is >= the secp256k1 group order", priv)
				}
				if scalar.IsZero() {
					t.Fatalf("PROVIDER_PRIVATE_KEY is zero")
				}
				derivedPub := "0x" + hex.EncodeToString(secp256k1.NewPrivateKey(&scalar).PubKey().SerializeUncompressed())

				printedPub := printedPublicKeyRe.FindString(printed)
				if printedPub == "" {
					t.Fatalf("run() did not print an uncompressed public key:\n%s", printed)
				}
				if printedPub != derivedPub {
					t.Errorf("public key shown to the user %s does not derive from the .env private key (derives %s)", printedPub, derivedPub)
				}

				// The same public key is recorded as a comment in .env, so a user
				// who missed the startup output can still find what to send t-0.
				// The scaffolder writes it under the private key on its own; the
				// starters' .env.example carry no marker for it.
				envRaw, err := os.ReadFile(envPath)
				if err != nil {
					t.Fatal(err)
				}
				switch recorded := printedPublicKeyRe.FindString(string(envRaw)); {
				case recorded == "":
					t.Errorf(".env does not record the public key under PROVIDER_PRIVATE_KEY:\n%s", envRaw)
				case recorded != derivedPub:
					t.Errorf("public key recorded in .env %s does not derive from the private key on the line above (derives %s)", recorded, derivedPub)
				}

				if _, dup := seenPriv[priv]; dup {
					t.Errorf("private key %s repeated across instantiations (first in %s) — not random", priv, seenPriv[priv])
				}
				seenPriv[priv] = name
				if _, dup := seenPub[derivedPub]; dup {
					t.Errorf("public key %s repeated across instantiations", derivedPub)
				}
				seenPub[derivedPub] = name

				// The key must not be read back from the template: the shipped
				// .env.example keeps its empty placeholder.
				example := parseDotenv(t, filepath.Join(projectDir, ".env.example"))
				if v := example["PROVIDER_PRIVATE_KEY"]; v != "" {
					t.Errorf(".env.example PROVIDER_PRIVATE_KEY = %q, want empty placeholder", v)
				}
				// NETWORK_PUBLIC_KEY must be present; the sandbox key is pre-filled
				// in .env.example so developers can run immediately.
				if _, ok := env["NETWORK_PUBLIC_KEY"]; !ok {
					t.Error("NETWORK_PUBLIC_KEY missing from .env")
				}

				if runtime.GOOS != "windows" {
					info, err := os.Stat(envPath)
					if err != nil {
						t.Fatal(err)
					}
					if perm := info.Mode().Perm(); perm != 0o600 {
						t.Errorf(".env mode = %o, want 0600 (it holds the private key)", perm)
					}
				}
			})
		}
	}

	if want := 2 * len(starterTargets(t)); len(seenPriv) != want {
		t.Errorf("%d distinct private keys across %d instantiations", len(seenPriv), want)
	}
}

package main

import (
	"bytes"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
)

// withConfig swaps the package-level Config for the test and restores it.
// No test in this package uses t.Parallel().
func withConfig(t *testing.T, c CLIConfig) {
	t.Helper()
	prev := Config
	Config = c
	t.Cleanup(func() { Config = prev })
}

func captureOutput(t *testing.T, fn func()) string {
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
	fn()
	w.Close()
	os.Stdout = orig
	<-done
	return buf.String()
}

func TestUsage_FollowsConfig(t *testing.T) {
	t.Run("product without go or a java repository choice", func(t *testing.T) {
		withConfig(t, CLIConfig{
			ProductName:  "usdt-pay",
			Command:      "usdt-pay init",
			RoleRequired: true,
			Languages:    []string{"java", "node"},
		})
		out := captureOutput(t, printUsage)
		for _, want := range []string{"Initialize a new usdt-pay project", "--role string        Role (required)"} {
			if !strings.Contains(out, want) {
				t.Errorf("usage lacks %q:\n%s", want, out)
			}
		}
		for _, unwanted := range []string{"--module", "--repository", "T-0 Network"} {
			if strings.Contains(out, unwanted) {
				t.Errorf("usage offers %q, which this product does not have:\n%s", unwanted, out)
			}
		}
	})

	t.Run("product with go, java repositories and no roles", func(t *testing.T) {
		withConfig(t, CLIConfig{
			ProductName:      "t0",
			Description:      "a new T-0 Network provider project",
			Languages:        []string{"go", "java"},
			JavaRepositories: []string{"jitpack", "maven-central"},
		})
		out := captureOutput(t, printUsage)
		for _, want := range []string{
			"Initialize a new T-0 Network provider project",
			"--module string      Go module path",
			"--repository string  Java SDK repository: jitpack|maven-central",
		} {
			if !strings.Contains(out, want) {
				t.Errorf("usage lacks %q:\n%s", want, out)
			}
		}
		if strings.Contains(out, "--role") {
			t.Errorf("usage offers --role although the product has no roles:\n%s", out)
		}
	})
}

func TestCompletion_PrintsNextSteps(t *testing.T) {
	withConfig(t, CLIConfig{
		ProductName: "usdt-pay",
		Languages:   []string{"java"},
		NextSteps:   []string{"Add NETWORK_PUBLIC_KEY to .env", "Deploy behind a public URL"},
	})
	out := captureOutput(t, func() {
		printCompletion(ScaffoldOpts{Lang: "java", ProjectDir: "demo"}, KeyPair{PublicKey: "0x04"})
	})
	for _, want := range []string{
		"1. Navigate to your project:",
		"2. Add NETWORK_PUBLIC_KEY to .env",
		"3. Deploy behind a public URL",
		"4. Run the application:",
	} {
		if !strings.Contains(out, want) {
			t.Errorf("completion lacks %q:\n%s", want, out)
		}
	}
}

func TestCompletion_RunStep(t *testing.T) {
	t.Run("lang/role entry wins over built-in", func(t *testing.T) {
		withConfig(t, CLIConfig{
			ProductName: "usdt-pay",
			Languages:   []string{"python"},
			RunSteps: map[string]RunStep{
				"python/acquirer": {Label: "Run the acquirer:", Command: "uv sync && uv run python -m acquirer.main"},
			},
		})
		out := captureOutput(t, func() {
			printCompletion(ScaffoldOpts{Lang: "python", Role: "acquirer", ProjectDir: "demo"}, KeyPair{PublicKey: "0x04"})
		})
		if !strings.Contains(out, "Run the acquirer:") {
			t.Errorf("override label missing:\n%s", out)
		}
		if !strings.Contains(out, "uv sync && uv run python -m acquirer.main") {
			t.Errorf("override command missing:\n%s", out)
		}
		if strings.Contains(out, "provider.main") {
			t.Errorf("built-in command leaked through:\n%s", out)
		}
	})

	t.Run("lang entry wins over built-in when no role entry matches", func(t *testing.T) {
		withConfig(t, CLIConfig{
			ProductName: "example",
			Languages:   []string{"python"},
			RunSteps: map[string]RunStep{
				"python": {Label: "Start the service:", Command: "uv sync && uv run python -m service.main"},
			},
		})
		out := captureOutput(t, func() {
			printCompletion(ScaffoldOpts{Lang: "python", Role: "issuer", ProjectDir: "demo"}, KeyPair{PublicKey: "0x04"})
		})
		if !strings.Contains(out, "Start the service:") {
			t.Errorf("lang-level override missing:\n%s", out)
		}
		if strings.Contains(out, "provider.main") {
			t.Errorf("built-in command leaked through:\n%s", out)
		}
	})

	t.Run("nil RunSteps keeps built-in", func(t *testing.T) {
		withConfig(t, CLIConfig{
			ProductName: "t0",
			Languages:   []string{"go"},
		})
		out := captureOutput(t, func() {
			printCompletion(ScaffoldOpts{Lang: "go", ProjectDir: "demo"}, KeyPair{PublicKey: "0x04"})
		})
		if !strings.Contains(out, "Run the application:") {
			t.Errorf("built-in label missing:\n%s", out)
		}
		if !strings.Contains(out, "go run ./cmd") {
			t.Errorf("built-in command missing:\n%s", out)
		}
	})

	t.Run("different role entry does not leak", func(t *testing.T) {
		withConfig(t, CLIConfig{
			ProductName: "usdt-pay",
			Languages:   []string{"python"},
			RunSteps: map[string]RunStep{
				"python/issuer": {Label: "Run the issuer:", Command: "uv sync && uv run python -m issuer.main"},
			},
		})
		out := captureOutput(t, func() {
			printCompletion(ScaffoldOpts{Lang: "python", Role: "acquirer", ProjectDir: "demo"}, KeyPair{PublicKey: "0x04"})
		})
		if strings.Contains(out, "issuer") {
			t.Errorf("wrong role entry leaked:\n%s", out)
		}
		if !strings.Contains(out, "provider.main") {
			t.Errorf("should fall back to built-in for python:\n%s", out)
		}
	})

	t.Run("numbering with NextSteps", func(t *testing.T) {
		withConfig(t, CLIConfig{
			ProductName: "usdt-pay",
			Languages:   []string{"python"},
			NextSteps:   []string{"Add NETWORK_PUBLIC_KEY to .env", "Deploy behind a public URL"},
			RunSteps: map[string]RunStep{
				"python/acquirer": {Label: "Run the acquirer:", Command: "uv sync && uv run python -m acquirer.main"},
			},
		})
		out := captureOutput(t, func() {
			printCompletion(ScaffoldOpts{Lang: "python", Role: "acquirer", ProjectDir: "demo"}, KeyPair{PublicKey: "0x04"})
		})
		if !strings.Contains(out, "4. Run the acquirer:") {
			t.Errorf("run step should be numbered 4 (1=cd, 2+3=NextSteps):\n%s", out)
		}
	})
}

func TestProcessPlaceholders_JavaPinsOnlyConfiguredArtifacts(t *testing.T) {
	withConfig(t, CLIConfig{
		Languages:        []string{"java"},
		JavaSDKArtifacts: []string{"network.t-0:provider-sdk-java"},
	})
	content := `implementation("network.t-0:provider-sdk-java:+")` + "\n" +
		`implementation("io.example:unrelated:+")` + "\n"

	got := processPlaceholders(content, ScaffoldOpts{Lang: "java", Version: "1.2.3"}, "")
	if !strings.Contains(got, `"network.t-0:provider-sdk-java:1.2.3"`) {
		t.Errorf("configured artifact not pinned:\n%s", got)
	}
	if !strings.Contains(got, `"io.example:unrelated:+"`) {
		t.Errorf("unrelated + dependency was rewritten:\n%s", got)
	}

	if got := processPlaceholders(content, ScaffoldOpts{Lang: "java", Version: "dev"}, ""); got != content {
		t.Errorf("dev build must not pin anything:\n%s", got)
	}

	withConfig(t, CLIConfig{Languages: []string{"java"}})
	if got := processPlaceholders(content, ScaffoldOpts{Lang: "java", Version: "1.2.3"}, ""); got != content {
		t.Errorf("product without JavaSDKArtifacts must not rewrite anything:\n%s", got)
	}
}

func TestProcessPlaceholders_JavaRepository(t *testing.T) {
	content := `val sdkRepository = "jitpack"` + "\n"

	withConfig(t, CLIConfig{Languages: []string{"java"}, JavaRepositories: []string{"jitpack", "maven-central"}})
	if got := processPlaceholders(content, ScaffoldOpts{Lang: "java", JavaRepo: "maven-central"}, ""); !strings.Contains(got, `val sdkRepository = "maven-central"`) {
		t.Errorf("chosen repository not applied:\n%s", got)
	}
	if got := processPlaceholders(content, ScaffoldOpts{Lang: "java", JavaRepo: "jitpack"}, ""); got != content {
		t.Errorf("default repository must leave the template alone:\n%s", got)
	}

	withConfig(t, CLIConfig{Languages: []string{"java"}})
	if got := processPlaceholders(content, ScaffoldOpts{Lang: "java", JavaRepo: "maven-central"}, ""); got != content {
		t.Errorf("product without JavaRepositories must not rewrite anything:\n%s", got)
	}
}

func TestWriteEnvFile_RecordsPublicKey(t *testing.T) {
	kp := KeyPair{PrivateKey: "0x" + strings.Repeat("ab", 32), PublicKey: "0x04" + strings.Repeat("cd", 64)}

	t.Run("no marker: comment line under the key", func(t *testing.T) {
		dir := t.TempDir()
		os.WriteFile(filepath.Join(dir, ".env.example"), []byte("# keys\nPROVIDER_PRIVATE_KEY=\nNETWORK_PUBLIC_KEY=\n"), 0o644)
		if err := writeEnvFile(dir, kp); err != nil {
			t.Fatal(err)
		}
		env, _ := os.ReadFile(filepath.Join(dir, ".env"))
		want := "# keys\nPROVIDER_PRIVATE_KEY=" + kp.PrivateKey + "\n# Public key for the line above (share it with t-0): " + kp.PublicKey + "\nNETWORK_PUBLIC_KEY=\n"
		if string(env) != want {
			t.Errorf(".env:\n%s\nwant:\n%s", env, want)
		}
	})

	t.Run("marker: replaced in place, no extra line", func(t *testing.T) {
		dir := t.TempDir()
		os.WriteFile(filepath.Join(dir, ".env.example"), []byte("PRIVATE_KEY=your_private_key_here\n# your_public_key_here\n"), 0o644)
		if err := writeEnvFile(dir, kp); err != nil {
			t.Fatal(err)
		}
		env, _ := os.ReadFile(filepath.Join(dir, ".env"))
		want := "PRIVATE_KEY=" + kp.PrivateKey + "\n# " + kp.PublicKey + "\n"
		if string(env) != want {
			t.Errorf(".env:\n%s\nwant:\n%s", env, want)
		}
	})

	t.Run("only the active assignment is replaced, whole line, comments untouched", func(t *testing.T) {
		dir := t.TempDir()
		example := "# PROVIDER_PRIVATE_KEY=example-in-a-comment\nPROVIDER_PRIVATE_KEY=0xdeadbeef\nOTHER_PRIVATE_KEY=keep\n"
		os.WriteFile(filepath.Join(dir, ".env.example"), []byte(example), 0o644)
		if err := writeEnvFile(dir, kp); err != nil {
			t.Fatal(err)
		}
		env, _ := os.ReadFile(filepath.Join(dir, ".env"))
		want := "# PROVIDER_PRIVATE_KEY=example-in-a-comment\nPROVIDER_PRIVATE_KEY=" + kp.PrivateKey +
			"\n# Public key for the line above (share it with t-0): " + kp.PublicKey + "\nOTHER_PRIVATE_KEY=keep\n"
		if string(env) != want {
			t.Errorf(".env:\n%s\nwant:\n%s", env, want)
		}
	})

	t.Run("existing .env with loose permissions is rewritten as 0600", func(t *testing.T) {
		if runtime.GOOS == "windows" {
			t.Skip("no POSIX modes")
		}
		dir := t.TempDir()
		os.WriteFile(filepath.Join(dir, ".env.example"), []byte("PROVIDER_PRIVATE_KEY=\n"), 0o644)
		os.WriteFile(filepath.Join(dir, ".env"), []byte("stale\n"), 0o666)
		if err := writeEnvFile(dir, kp); err != nil {
			t.Fatal(err)
		}
		info, _ := os.Stat(filepath.Join(dir, ".env"))
		if perm := info.Mode().Perm(); perm != 0o600 {
			t.Errorf(".env mode = %o, want 0600", perm)
		}
	})

	t.Run("no .env.example: nothing written", func(t *testing.T) {
		dir := t.TempDir()
		if err := writeEnvFile(dir, kp); err != nil {
			t.Fatal(err)
		}
		if _, err := os.Stat(filepath.Join(dir, ".env")); err == nil {
			t.Error(".env written although the template has no .env.example")
		}
	})
}

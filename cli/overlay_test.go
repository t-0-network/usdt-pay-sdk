package main

import (
	"io/fs"
	"os"
	"path"
	"path/filepath"
	"testing"
)

func TestConfig_PostScaffoldWired(t *testing.T) {
	if Config.PostScaffold == nil {
		t.Fatal("Config.PostScaffold is nil — overlay will not be applied")
	}
}

func TestOverlay_AppliedPerRole(t *testing.T) {
	for _, lang := range Config.Languages {
		roles, _ := listRoles(lang)
		if len(roles) == 0 {
			roles = []string{""}
		}
		for _, role := range roles {
			name := lang
			overlayRoot := path.Join("overlay", lang)
			if role != "" {
				name = lang + "/" + role
				overlayRoot = path.Join(overlayRoot, role)
			}

			if _, err := overlayFiles.ReadDir(overlayRoot); err != nil {
				t.Logf("no overlay for %s — skipping", name)
				continue
			}

			t.Run(name, func(t *testing.T) {
				dir := t.TempDir()
				projectDir := filepath.Join(dir, "test-project")

				opts := ScaffoldOpts{
					Lang:        lang,
					Role:        role,
					ProjectName: "test-project",
					ProjectDir:  projectDir,
					Version:     "dev",
				}
				if lang == "go" {
					opts.ModulePath = "github.com/test/test-project"
				}

				if err := os.MkdirAll(projectDir, 0777); err != nil {
					t.Fatal(err)
				}
				if err := scaffold(opts); err != nil {
					t.Fatalf("scaffold(%s): %v", name, err)
				}
				if err := Config.PostScaffold(opts); err != nil {
					t.Fatalf("PostScaffold(%s): %v", name, err)
				}

				if err := fs.WalkDir(overlayFiles, overlayRoot, func(src string, d fs.DirEntry, walkErr error) error {
					if walkErr != nil || d.IsDir() {
						return walkErr
					}
					rel := src[len(overlayRoot)+1:]
					want, _ := overlayFiles.ReadFile(src)
					got, err := os.ReadFile(filepath.Join(projectDir, rel))
					if err != nil {
						t.Errorf("overlay file %s missing in output: %v", rel, err)
						return nil
					}
					if string(got) != string(want) {
						t.Errorf("overlay file %s differs from embedded version", rel)
					}
					return nil
				}); err != nil {
					t.Fatalf("walking overlay %s: %v", overlayRoot, err)
				}

				for _, required := range []string{".dockerignore", "Dockerfile"} {
					if _, err := os.Stat(filepath.Join(projectDir, required)); err != nil {
						t.Errorf("%s missing after overlay", required)
					}
				}
			})
		}
	}
}

func TestRun_AppliesOverlay(t *testing.T) {
	dir := t.TempDir()
	projectDir := filepath.Join(dir, "test-project")

	opts := ScaffoldOpts{
		Lang:        "java",
		Role:        "acquirer",
		ProjectName: "test-project",
		ProjectDir:  projectDir,
		Version:     "dev",
	}

	if err := run(opts); err != nil {
		t.Fatalf("run: %v", err)
	}

	overlayDockerfile, err := overlayFiles.ReadFile("overlay/java/acquirer/Dockerfile")
	if err != nil {
		t.Fatalf("reading overlay Dockerfile: %v", err)
	}

	got, err := os.ReadFile(filepath.Join(projectDir, "Dockerfile"))
	if err != nil {
		t.Fatalf("reading scaffolded Dockerfile: %v", err)
	}

	if string(got) != string(overlayDockerfile) {
		t.Error("scaffolded Dockerfile does not match overlay — run() may not be calling PostScaffold")
	}
}

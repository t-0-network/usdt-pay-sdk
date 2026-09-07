package main

import (
	"bytes"
	"io/fs"
	"os"
	"path"
	"path/filepath"
	"strings"
	"testing"
)

// starterTargets lists every lang/role pair with an embedded starter template,
// so a new starter is covered by these tests without editing them.
func starterTargets(t *testing.T) [][2]string {
	t.Helper()
	var targets [][2]string
	for _, lang := range Config.Languages {
		roles, err := listRoles(lang)
		if err != nil {
			t.Fatalf("listRoles(%s): %v — run 'go generate ./...' first", lang, err)
		}
		if len(roles) == 0 {
			roles = []string{""}
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

func overlayRootFor(lang, role string) string {
	root := path.Join("overlay", lang)
	if role != "" {
		root = path.Join(root, role)
	}
	return root
}

func TestConfig_OverlayWired(t *testing.T) {
	if Config.OverlayFS == nil {
		t.Fatal("Config.OverlayFS is nil — scaffolded projects would ship the monorepo Dockerfile")
	}
}

func TestOverlay_EveryStarterHasOne(t *testing.T) {
	for _, tgt := range starterTargets(t) {
		lang, role := tgt[0], tgt[1]
		root := overlayRootFor(lang, role)
		for _, name := range []string{"Dockerfile", ".dockerignore"} {
			if _, err := fs.Stat(overlayFiles, path.Join(root, name)); err != nil {
				t.Errorf("%s/%s: cli/%s/%s missing — the in-repo one cannot ship standalone", lang, role, root, name)
			}
		}
	}
}

func TestRun_InstantiatesEveryStarter(t *testing.T) {
	for _, tgt := range starterTargets(t) {
		lang, role := tgt[0], tgt[1]
		t.Run(lang+"/"+role, func(t *testing.T) {
			projectDir := filepath.Join(t.TempDir(), "test-project")
			opts := ScaffoldOpts{
				Lang:        lang,
				Role:        role,
				ProjectName: "test-project",
				ProjectDir:  projectDir,
				Version:     "dev",
			}
			if err := run(opts); err != nil {
				t.Fatalf("run: %v", err)
			}

			for _, name := range []string{".gitignore", ".env"} {
				if _, err := os.Stat(filepath.Join(projectDir, name)); err != nil {
					t.Errorf("%s missing after run(): %v", name, err)
				}
			}

			root := overlayRootFor(lang, role)
			err := fs.WalkDir(overlayFiles, root, func(src string, d fs.DirEntry, err error) error {
				if err != nil || d.IsDir() {
					return err
				}
				rel := strings.TrimPrefix(src, root+"/")
				want, err := fs.ReadFile(overlayFiles, src)
				if err != nil {
					return err
				}
				got, err := os.ReadFile(filepath.Join(projectDir, filepath.FromSlash(rel)))
				if err != nil {
					t.Errorf("overlay file %s not in output: %v", rel, err)
					return nil
				}
				if !bytes.Equal(got, want) {
					t.Errorf("output %s differs from cli/%s/%s — overlay not applied", rel, root, rel)
				}
				return nil
			})
			if err != nil {
				t.Fatalf("walking cli/%s: %v", root, err)
			}
		})
	}
}

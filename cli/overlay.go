package main

import (
	"embed"
	"fmt"
	"io/fs"
	"os"
	"path"
	"path/filepath"
	"strings"
)

//go:embed all:overlay
var overlayFiles embed.FS

func applyOverlay(opts ScaffoldOpts) error {
	overlayRoot := path.Join("overlay", opts.Lang)
	if opts.Role != "" {
		overlayRoot = path.Join(overlayRoot, opts.Role)
	}

	if _, err := overlayFiles.ReadDir(overlayRoot); err != nil {
		return nil
	}

	return fs.WalkDir(overlayFiles, overlayRoot, func(src string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if d.IsDir() {
			return nil
		}

		rel := strings.TrimPrefix(src, overlayRoot+"/")

		data, err := overlayFiles.ReadFile(src)
		if err != nil {
			return fmt.Errorf("reading overlay file %s: %w", src, err)
		}

		destPath := filepath.Join(opts.ProjectDir, rel)
		if err := os.MkdirAll(filepath.Dir(destPath), 0777); err != nil {
			return err
		}
		return os.WriteFile(destPath, data, 0666)
	})
}

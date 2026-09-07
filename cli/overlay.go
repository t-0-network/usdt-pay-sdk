package main

import "embed"

// Standalone Dockerfiles, .dockerignore and READMEs per overlay/<lang>/<role>,
// written over the monorepo-context originals by the synced scaffolder via
// Config.OverlayFS. all: so dotfiles are embedded.
//
//go:embed all:overlay
var overlayFiles embed.FS

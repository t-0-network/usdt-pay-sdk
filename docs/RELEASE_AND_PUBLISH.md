# Release & Publish

Two workflows make a release. Neither does the other's job:

- **[`release.yaml`](../.github/workflows/release.yaml)** — manual dispatch. Bumps every version
  site, validates them, commits, tags `vX.Y.Z` and creates the GitHub Release. **Publishes
  nothing.**
- **[`publish.yaml`](../.github/workflows/publish.yaml)** — fires on that tag. Builds, re-checks
  each ecosystem's version against the tag, then publishes to npm and Maven Central and uploads
  the CLI jar to the Release.

A complete release is `release.yaml` → tag → `publish.yaml`. **Never trigger `publish.yaml` by
hand and never `git tag vX.Y.Z` by hand** — both registries are immutable, and there is no dry-run
mode.

```
gh workflow run release.yaml -f bump=patch --ref master
```

---

## Version sites

One version, five places. `release.yaml` moves all of them in a single commit and refuses to push
if any disagrees.

| File | Field |
|---|---|
| `java/gradle.properties` | `version=X.Y.Z` |
| `node/sdk/package.json` | `.version` |
| `node/starter/issuer/package.json` | `.version` |
| `node/starter/issuer/package.json` | `.dependencies["@t-0/usdt-pay-sdk"]` = `^X.Y.Z` |
| `node/package-lock.json` | `packages["sdk"].version`, `packages["starter/issuer"].version`, and the starter's pin |

The Java side has exactly one site: `java/cli/src/main/resources/version.properties` is `expand`ed
from `project.version` at build time (`java/cli/build.gradle.kts`), so it follows automatically.

**Not a version site:** `java/starter/acquirer/build.gradle.kts`'s `version = "0.1.0-SNAPSHOT"`.
That is the *scaffolded project's own* version — the file ships as a template inside
`usdt-pay-init.jar` and is never published. The same file takes no SDK pin either: standalone
builds pass `-PusdtPaySdkVersion=<version>`, deliberately not a `+` range, because an SDK bump is
the consumer's decision. Left alone on purpose; don't "fix" it.

---

## release.yaml

Dispatch input `bump` — `patch` (default) / `minor` / `major`.

1. **Build gate** — `build-java` and `build-node`, the same two jobs as `ci.yaml` including
   `npm audit --omit=dev --audit-level=high`. A red tree cannot be released.
2. **`release` job**, guarded by `if: github.ref == 'refs/heads/master'` so a dispatch against a
   feature branch cannot tag.
   1. Mint a GitHub App token (`vars.CI_APP_CLIENT_ID` + `secrets.CI_APP_PRIVATE_KEY`, scoped to
      this repo, `permission-contents: write`). Every write in the job uses it; `GITHUB_TOKEN`
      stays read-only. The token is also what pushes to protected `master` — the org ruleset
      `default-branch-protection` grants the t-0-ci App `bypass_mode: always`.
   2. **Calculate version.** With no tags yet, the tree's own version *is* the release and the
      `bump` input is ignored — otherwise the first release would ship `0.1.1` and skip `0.1.0`.
      With tags, the latest `vX.Y.Z` is parsed and incremented.
   3. **Bump.** Node via `npm version … --no-workspaces-update -w sdk -w starter/issuer`, then
      `npm pkg set` for the starter's pin, then `npm install --package-lock-only`. Java via `sed`
      on `gradle.properties`. Nothing hand-edits the lockfile.
   4. **Validate** all five sites plus the three lockfile entries. Any mismatch fails *before* the
      push.
   5. **Commit, tag, push** with plain git and `--allow-empty` / `--atomic` (see below).
   6. **Create the GitHub Release**, idempotently.

### Two flags that look cosmetic and are not

- **`--no-workspaces-update`** on `npm version`. Without it npm does an implicit workspace reify.
  At that moment `sdk` is already at the new version while the starter still pins `^OLD`, so the
  workspace link no longer satisfies the pin and npm goes to the registry — which may not have a
  version satisfying the *old* pin. Result: `npm error 404`, exit 1, with both `package.json`
  files already rewritten. It breaks every minor/major dispatch, not just the first one.
- **`git commit --allow-empty`**. On the initial release every site already reads the target
  version, so the diff is empty. An auto-commit action would skip the commit *and* the tag and
  report success — a green run that released nothing. Plain git gives one code path for both cases.
- **`git push --atomic`**. Without it the branch ref can land while the tag push fails; a
  re-dispatch would then bump from the old latest tag against a master that already carries the
  bump commit — a double bump.

---

## publish.yaml

Fires on `v[0-9]+.[0-9]+.[0-9]+`.

**Build gate** — `build-java` + `build-node` again, **minus `npm audit`**. Deliberate: pre-tag a
fresh advisory should block the release; post-tag it must not, or an advisory published in the
minutes between tag and publish strands a tagged release that cannot be re-cut without a
dependency bump.

Both publish jobs `needs:` both build jobs — every ecosystem builds before anything publishes.

**Preflights run first in each publish job**, because the two jobs run in parallel against
immutable registries and a missing prerequisite must fail before anything irreversible:

| Job | Preflight |
|---|---|
| `publish-node` | repository is public (`gh api repos/$GITHUB_REPOSITORY --jq .private` = `false`) |
| `publish-java` | `vars.OSSRH_USERNAME`, `secrets.OSSRH_PASSWORD`, `secrets.GPG_PRIVATE_KEY` all non-empty |

### `publish-node`

`ubuntu-latest` — npm rejects `--provenance` from self-hosted runners. `id-token: write`, no
`NPM_TOKEN`, and no `registry-url` on `setup-node` (that would wire up a `NODE_AUTH_TOKEN` path
that must not exist). Auth is entirely the package's trusted publisher.

Node 22 ships npm 10.x, which has no OIDC support, so the job installs `npm@^11.5.1` and asserts
the result. Pinned to the 11 major rather than `@latest`: a future npm 12 could raise its own Node
floor above 22 and break the job on a day nobody touched the repo.

Then: version-matches-tag, `npm ci` (the lockfile is at `node/`, not `node/sdk/`), build +
typecheck the SDK, assert the package is `@t-0/usdt-pay-sdk` and not `private`, `npm pack
--dry-run`, `npm publish --provenance --access public`.

### `publish-java`

Version-matches-tag, `./gradlew build --no-daemon` (also the only thing that produces the CLI jar —
`publishAggregationToCentralPortal` does not build it), `publishAggregationToCentralPortal`, then a
bounded poll for the Release and `gh release upload` of both
`usdt-pay-init-<version>.jar` and an unversioned `usdt-pay-init.jar` copy. The unversioned copy is
what lets the README name a download URL that survives every release.

---

## Required repository configuration

| Name | Kind | Used by | Provisioned by |
|---|---|---|---|
| `CI_APP_CLIENT_ID` | org variable | both workflows | already org-wide, visibility `all` |
| `CI_APP_PRIVATE_KEY` | org secret | both workflows | already org-wide, visibility `all` |
| `OSSRH_USERNAME` | repo variable | `publish-java` | `backend/infra` |
| `OSSRH_PASSWORD` | repo secret | `publish-java` | `backend/infra` |
| `GPG_PRIVATE_KEY` | repo secret | `publish-java` | `backend/infra` |

No ruleset or GitHub App change is needed: the org ruleset `default-branch-protection` already
grants the t-0-ci App `bypass_mode: always` on `refs/heads/master` for `~ALL` repos.

npm needs no secret at all — publishing is OIDC through the package's trusted publisher.

---

## Before the first release

In this order. The preflights enforce it, but they enforce it by failing a run.

1. **Make the repository public.** `npm publish --provenance` returns 422 *"Only public source
   repositories are supported"* from a private repo, and the README's promise that
   `usdt-pay-init.jar` is downloadable from each Release needs it too.

   ```bash
   gh repo edit t-0-network/usdt-pay-sdk --visibility public --accept-visibility-change-consequences
   ```

   This publishes **every remote branch**, including abandoned `feature/*` and `codex/*` ones, and
   is effectively irreversible. Not Pulumi-managed — `gh` is the only mechanism.

2. **Apply the infra change** that creates `OSSRH_USERNAME` / `OSSRH_PASSWORD` /
   `GPG_PRIVATE_KEY` (`pulumi up` on the **prod** stack).

3. **Bootstrap the npm package.** A trusted publisher can only be configured on a package that
   already exists, and `@t-0/usdt-pay-sdk` does not. A `@t-0` maintainer, with explicit
   authorization, publishes one interactive 2FA-protected prerelease under a non-`latest` dist-tag
   — e.g. `npm publish --tag bootstrap` at `0.1.0-bootstrap.0`, leaving `0.1.0` free for the
   automated release — then configures the package's GitHub Actions trusted publisher:

   | Field | Value |
   |---|---|
   | Organization | `t-0-network` |
   | Repository | `usdt-pay-sdk` |
   | Workflow filename | `publish.yaml` |
   | Allowed action | `npm publish` |
   | Environment | *(blank)* |

   Verify one OIDC run before revoking any token-based publish rights.

4. **Dispatch `release.yaml`.** The first run ignores the `bump` input and ships the tree version.

---

## Operating notes

### Registry publication is immutable

npm and Maven Central both refuse to replace a published version. If npm succeeds and Central
fails, **a re-run cannot fix it** — the npm version is spent. Recovery is a new patch release,
decided deliberately. Never re-run a publish job blind: read which registries actually got the
artifact first.

The two publish jobs run in parallel by design. Sequencing them would not remove the partial-release
window, only change which registry gets stranded.

### The jar upload can be stranded

`publish-java`'s `gh release upload` sits *after* the non-idempotent Central publish. If only the
upload fails, a job re-run dies earlier at Central's duplicate-version rejection and never reaches
it. Recover by hand from a checkout of the tag:

```bash
cd java && ./gradlew build --no-daemon
cp cli/build/libs/usdt-pay-init-X.Y.Z.jar cli/build/libs/usdt-pay-init.jar
gh release upload vX.Y.Z \
  cli/build/libs/usdt-pay-init-X.Y.Z.jar cli/build/libs/usdt-pay-init.jar --clobber
```

### Why versions are validated twice

`release.yaml` validates that the bump it just performed is internally consistent.
`publish.yaml` validates that the **tagged commit** still agrees with the tag — which catches a
hand-pushed tag, a revert that left a tag behind, or a new version site added to one workflow but
not the other. Both are a few greps.

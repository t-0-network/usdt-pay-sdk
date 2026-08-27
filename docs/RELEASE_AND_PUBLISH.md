# Release & Publish

Two workflows make a release. Neither does the other's job:

- **[`release.yaml`](../.github/workflows/release.yaml)** — manual dispatch. Bumps every version
  site, validates them, commits, tags `vX.Y.Z` and creates the GitHub Release. **Publishes
  nothing.**
- **[`publish.yaml`](../.github/workflows/publish.yaml)** — fires on that tag. Builds, re-checks
  each package's version against the tag, then publishes the npm package and the Java artifacts
  to Maven Central.

A complete release is `release.yaml` → tag → `publish.yaml`. **Never trigger `publish.yaml` by
hand and never `git tag vX.Y.Z` by hand** — both registries are immutable, and there is no dry-run
mode.

```
gh workflow run release.yaml -f bump=patch --ref master
```

---

## Version sites

One version, everywhere. `release.yaml` moves all of it in a single commit and refuses to push if
any site disagrees.

| File | Field |
|---|---|
| `java/gradle.properties` | `version=X.Y.Z` |
| `java/starter/acquirer/gradle.properties` | `usdtPaySdkVersion=X.Y.Z` |
| `java/sdk/src/main/java/network/t0/pay/server/Version.java` | `SDK_VERSION` |
| `node/sdk/src/version.ts` | `SDK_VERSION` |
| `node/sdk/package.json` | `.version` |
| `node/starter/issuer/package.json` | `.version` |
| `node/starter/issuer/package.json` | `.dependencies["@t-0/usdt-pay-sdk"]` = `^X.Y.Z` |
| `node/package-lock.json` | the `sdk` and `starter/issuer` entries — two `.version`s and the starter SDK pin |

---

## Starters

Starters are live, tested projects under `java/starter/` and `node/starter/`. They are **not
published as packages** — the unified CLI in `cli/` (`usdt-pay init`) embeds them as templates at
build time via `go generate`.

| Platform | Starter source | Role |
|---|---|---|
| Java | `java/starter/acquirer/` | acquirer |
| Node | `node/starter/issuer/` | issuer |

Adding a role is adding a directory under the appropriate `starter/` and wiring it into
`cli/generate.go`. `cli/overlay/<lang>/<role>/` holds files that cannot ship verbatim — currently
the Dockerfiles, whose in-repo form takes the workspace as its build context while a scaffolded
project resolves the SDK from a registry.

**Not a version site:** `java/starter/acquirer/build.gradle.kts`'s `version = "0.1.0-SNAPSHOT"`.
That is the *scaffolded project's own* version — the file ships as a template and is never
published. The SDK pin lives in `java/starter/acquirer/gradle.properties` as `usdtPaySdkVersion`
(a version site — see the table above); `-PusdtPaySdkVersion=<version>` overrides it for a one-off
build. Deliberately not a `+` range, because an SDK bump is the consumer's decision.

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
   2. **Check the npm package exists**, the same bootstrap gate `publish.yaml`'s `preflight`
      carries — but ahead of the tag. Failing on the publish side instead would leave a public
      tag and a Release with no artifacts behind it, and the intuitive recovery (re-dispatch)
      would compute the *next* version and strand that tag empty forever. The recovery from a
      publish-side stop is always **Re-run failed jobs** on that run, never a re-dispatch.
   3. **Calculate version.** With no tags yet, the tree's own version *is* the release and the
      `bump` input is ignored — otherwise the first release would ship `0.1.1` and skip `0.1.0`.
      With tags, the latest `vX.Y.Z` is parsed and incremented. This branch reads
      `java/gradle.properties` verbatim, so that version must be a plain `X.Y.Z` — a `-SNAPSHOT`
      suffix would tag something `publish.yaml`'s tag filter does not match, and the publish would
      never fire.
   4. **Bump.** Node via `npm version … --no-workspaces-update -w sdk -w starter/issuer`,
      then `npm pkg set` for the starter SDK pin, then `npm install --package-lock-only`. Java via
      `sed` on `gradle.properties` and `Version.java`. Nothing hand-edits the lockfile.
   5. **Validate** every site in the table above, lockfile entries included. Any mismatch fails
      *before* the push.
   6. **Commit, tag, push** with plain git and `--allow-empty` / `--atomic` (see below).
   7. **Create the GitHub Release**, idempotently.

### Two flags that look cosmetic and are not

- **`--no-workspaces-update`** on `npm version`. Without it npm does an implicit workspace reify.
  At that moment `sdk` is already at the new version while the starter still pins `^OLD`,
  so that workspace link no longer satisfies its pin and npm goes to the registry — which may
  not have a version satisfying the *old* pin. Result: `npm error 404`, exit 1, with every
  `package.json` already rewritten. It breaks every minor/major dispatch, not just the first one.
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

```
    preflight            build-java            build-node
         \                    |                    /
          \-------------------+-------------------/
                              |
                   /----------+----------\
                  /                       \
        publish-node-sdk              publish-java
```

Nothing publishes until everything builds and the shared **`preflight`** job passes.

`preflight` checks *both* registries' prerequisites in one place:

- the repository is public (`gh api repos/$GITHUB_REPOSITORY --jq .private` = `false`, which npm
  provenance requires);
- `vars.OSSRH_USERNAME` / `secrets.OSSRH_PASSWORD` / `secrets.GPG_PRIVATE_KEY` are all non-empty;
- the npm package already exists on the registry — a trusted publisher can only be attached to a
  package that exists, so this is the [bootstrap](#before-the-first-release) having happened.
  Necessary, not sufficient: it cannot see whether the trusted publisher is actually configured,
  and nothing here can. A misconfigured one surfaces as a failed publish — before the upload, so
  nothing is spent.

That it is *shared* is the point. Per-job preflights would let `publish-java` spend the Maven
Central version while `publish-node` was still failing its visibility check — and a spent Central
version cannot be un-spent. A prerequisite missing for either registry now stops both.

### `publish-node-sdk`

`ubuntu-latest` — npm rejects `--provenance` from self-hosted runners. `id-token: write`, no
`NPM_TOKEN`, and no `registry-url` on `setup-node` (that would wire up a `NODE_AUTH_TOKEN` path
that must not exist). Auth is entirely the package's trusted publisher.

Node 22 ships npm 10.x, which has no OIDC support, so the job installs `npm@^11.5.1` and asserts
the result. Pinned to the 11 major rather than `@latest`: a future npm 12 could raise its own Node
floor above 22 and break the job on a day nobody touched the repo.

Then: version-matches-tag, `npm ci` (the lockfile is at `node/`, not in the package dir), build +
typecheck, assert the package is the name it should be and not `private`, `npm pack --dry-run`,
publish.

| Job | Package | Directory |
|---|---|---|
| `publish-node-sdk` | `@t-0/usdt-pay-sdk` | `node/sdk` |

### `publish-java`

Version-matches-tag, `./gradlew build --no-daemon`, `publishAggregationToCentralPortal`.

JitPack needs no job at all: it builds from the tag on demand, the first time someone requests the
coordinate.

---

## Required repository configuration

| Name | Kind | Used by | Provisioned by |
|---|---|---|---|
| `CI_APP_CLIENT_ID` | org variable | `release.yaml` | already org-wide, visibility `all` |
| `CI_APP_PRIVATE_KEY` | org secret | `release.yaml` | already org-wide, visibility `all` |
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
   repositories are supported"* from a private repo.

   ```bash
   gh repo edit t-0-network/usdt-pay-sdk --visibility public --accept-visibility-change-consequences
   ```

   This publishes **every remote branch**, including abandoned `feature/*` and `codex/*` ones, and
   is effectively irreversible. Not Pulumi-managed — `gh` is the only mechanism.

2. ~~**Apply the infra change** that creates `OSSRH_USERNAME` / `OSSRH_PASSWORD` /
   `GPG_PRIVATE_KEY`.~~ Done — [backend#1404](https://github.com/t-0-network/backend/pull/1404) is
   merged and applied to the **prod** stack.

3. **Bootstrap the npm package.** A trusted publisher can only be configured on a package that
   already exists, and `@t-0/usdt-pay-sdk` must exist before the first release. Both `release.yaml`
   and `publish.yaml` refuse to run until it does, so this step gates the first release entirely.

   A `@t-0` maintainer — with explicit authorization — publishes one interactive 2FA-protected
   prerelease under a non-`latest` dist-tag, which leaves `0.1.0` free for the automated release.
   From `node/`:

   ```bash
   # Local and uncommitted — this version number must never reach a commit.
   npm version 0.1.0-bootstrap.0 --no-git-tag-version --no-workspaces-update -w sdk
   npm publish -w sdk --tag bootstrap --access public
   git checkout -- sdk/package.json
   ```

   Then attach the package's trusted publisher. From the CLI (npm >= 11.15, as the 2FA maintainer):

   ```bash
   npm trust github @t-0/usdt-pay-sdk --repo t-0-network/usdt-pay-sdk --file publish.yaml --allow-publish
   ```

   or in the package's web settings, with the same values:

   | Field | Value |
   |---|---|
   | Organization | `t-0-network` |
   | Repository | `usdt-pay-sdk` |
   | Workflow filename | `publish.yaml` |
   | Allowed action | `npm publish` |
   | Environment | *(blank)* |

   Verify one OIDC run before revoking any token-based publish rights.

4. **Check the Java signing prerequisites**, neither of which `preflight` can see. The GPG key
   must be **passphrase-less** — `java/sdk/build.gradle.kts` passes an empty passphrase to
   `useInMemoryPgpKeys` — and its **public half must be on a public keyserver**
   (`keyserver.ubuntu.com` or `keys.openpgp.org`), or Central rejects the deployment at
   validation. Both failures are clean: they happen before publication, so nothing is spent.

   `OSSRH_USERNAME` / `OSSRH_PASSWORD` must be Central **Portal user tokens** for the account that
   owns the verified `network.t-0` namespace, not legacy OSSRH credentials.

5. **Dispatch `release.yaml`.** The first run ignores the `bump` input and ships the tree version.

---

## Operating notes

### Registry publication is immutable

npm and Maven Central both refuse to replace a published version. Never re-run a publish job
blind: read which registries actually received the artifact first.

What a failure costs depends on where it happened, and the two cases are not the same:

- **The publish never reached the registry** — an npm auth failure, or a Central *validation*
  failure (bad signature, unreachable keyserver, wrong credentials). Nothing was spent. Fix the
  cause and use *Re-run failed jobs* at the **same** version.
- **The publish landed and something after it failed.** That version is spent. If the fix needs a
  new commit, recovery is a new patch release, decided deliberately — not a re-run.

`publish-java` and `publish-node-sdk` run independently of each other — one side failing does not
stop the other — so a partial release across registries is possible in either direction: npm
published and Central not, or the reverse. Neither is worse than the other to sit in, and both
recover the same way, with *Re-run failed jobs* at the same version.

**Re-running after a partial success:** use *Re-run failed jobs*, never *Re-run all jobs* — the
latter re-enters a publish that already succeeded and fails on the duplicate.

### Why versions are validated twice

`release.yaml` validates that the bump it just performed is internally consistent.
`publish.yaml` validates that the **tagged commit** still agrees with the tag — which catches a
hand-pushed tag, a revert that left a tag behind, or a new version site added to one workflow but
not the other. Both are a few greps.

---

## Adding an ecosystem

provider-sdk already ships every ecosystem planned here; copy its workflow structure rather than
inventing one. Its [`RELEASE_AND_PUBLISH.md`](https://github.com/t-0-network/provider-sdk/blob/master/docs/RELEASE_AND_PUBLISH.md)
is the canonical reference for the two-stage flow, and its
[`VERSIONING.md`](https://github.com/t-0-network/provider-sdk/blob/master/docs/VERSIONING.md) for
the version-site taxonomy.

Every ecosystem needs the same three version sites, wired into both workflows:

- **A — package version**: the package manager's field (`package.json`, `gradle.properties`,
  `pyproject.toml`, `.csproj <Version>`, the Go module's tag).
- **B — starter template pin**: the starter's dependency on the SDK.
- **C — runtime constant**: the file a running server reports its version from.

And the same four workflow touchpoints: a bump step for A+B+C in `release.yaml`, a validate
assertion there for each, a `build-*` job in `publish.yaml`'s gate, and a `publish-*` job with the
version-matches-tag assertion. The [version sites table](#version-sites) above grows a row per site
— `publish.yaml`'s validation must grow with it, or the twice-validation stops catching drift.

### Go

Copy provider-sdk's [`publish-go` job](https://github.com/t-0-network/provider-sdk/blob/master/.github/workflows/publish.yaml)
wholesale — Go publishing has the most moving parts and all of them were learned the hard way
(provider-sdk [#251](https://github.com/t-0-network/provider-sdk/pull/251),
[#255](https://github.com/t-0-network/provider-sdk/pull/255)):

- Multi-module tags `go/vX.Y.Z` per module, created in **`publish.yaml`**, not `release.yaml` —
  they must not exist before the modules' `go.sum`s are proven.
- **sumtool** precomputes `go.sum` against a local `file://` GOPROXY before the tag exists, and the
  publish step verifies with `-mod=readonly` — a missing or wrong hash fails there.
- `GOPROXY="file://${PROXY_DIR},direct"` in CI, **never `proxy.golang.org`**: its negative caching
  races the fresh tag and can poison the module for everyone (that race is what broke
  provider-sdk's v1.1.29). `GONOSUMDB` covers our own modules so `sum.golang.org` is not consulted
  for tags it has not seen.
- A `LICENSE` file must sit **in each module directory**. `cmd/go` synthesizes the repo-root
  LICENSE into the module zip; `x/mod/zip.CreateFromDir` does not — and the two producing
  different zips is a checksum divergence users see as a security error.

### Python

Copy provider-sdk's `publish-python-sdk` / `publish-python-starter` jobs:

- PyPI trusted publishing — OIDC via `uv publish --trusted-publishing always`, no API tokens.
- One GitHub environment per package (`pypi-sdk`, `pypi-starter`) — PyPI's trusted-publisher
  config names the environment, so they cannot share one.
- `uv build --package <name>` for targeted builds in the monorepo.
- Version site C is `_version.py`.

### C# / NuGet

Copy provider-sdk's `publish-csharp` job:

- OIDC login via `NuGet/login@v1`, a dedicated `nuget` GitHub environment, no long-lived API key.
- `dotnet pack`, then `dotnet nuget push --skip-duplicate` — the skip is what makes the job
  re-runnable after a partial failure.
- `<Version>` in the `.csproj` is the single version source (site A and C in one).

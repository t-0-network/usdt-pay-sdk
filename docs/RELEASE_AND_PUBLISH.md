# Release & Publish

Two workflows make a release. Neither does the other's job:

- **[`release.yaml`](../.github/workflows/release.yaml)** — manual dispatch. Bumps every version
  site, validates them, commits, tags `vX.Y.Z` and creates the GitHub Release. **Publishes
  nothing.**
- **[`publish.yaml`](../.github/workflows/publish.yaml)** — fires on that tag. Builds, re-checks
  each package's version against the tag, then publishes both npm packages and the Java artifacts
  to Maven Central, and uploads the CLI jar to the Release.

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
| `node/sdk/package.json` | `.version` |
| `node/cli/package.json` | `.version` |
| `node/cli/package.json` | `.dependencies["@t-0/usdt-pay-sdk"]` = `^X.Y.Z` |
| `node/starter/issuer/package.json` | `.version` |
| `node/starter/issuer/package.json` | `.dependencies["@t-0/usdt-pay-sdk"]` = `^X.Y.Z` |
| `node/package-lock.json` | the `sdk`, `cli` and `starter/issuer` entries — three `.version`s and the two SDK pins |

The Java side has exactly one site: `java/cli/src/main/resources/version.properties` is `expand`ed
from `project.version` at build time (`java/cli/build.gradle.kts`), so it follows automatically.

---

## How the starters get published

No starter is published as a package. Each ships *inside a generator* — **one generator per
platform, with the role as a parameter**, not one generator per role:

| Platform | Generator | Role selection | Starter source | Packed by |
|---|---|---|---|---|
| Java | `usdt-pay-init.jar`, a Release asset | trailing `<role>` | every `java/starter/*`, live Gradle subprojects | `processResources` at build time |
| Node | `@t-0/usdt-pay-starter-ts` on npm | trailing `<role>` | every `node/starter/*`, live npm workspace members | `node/cli` `prepack` at pack/publish time |
| Go | *(later)* | | | |

In both, **the template listing is the role set** — `templates/<role>` in the jar,
`template/<role>` in the tarball. Adding a role is adding a directory under
`java/starter/` or `node/starter/`; there is no registry to update in either generator, and
no second initializer to write.

Both take the role as a **required trailing positional**, and neither has a default:

```
usdt-pay-init.jar    [project-name] <role>
usdt-pay-starter-ts  [project-name] <role>
```

Acquirer, issuer and lp are different integrations, and a default would be a contract that
changes underneath callers: the role list is sorted, so with one starter packed a bare
invocation would mean that role and adding a second starter would change what the identical
command does. Because the role cannot be omitted, a lone positional is unambiguously the role,
and the project name is prompted for instead.

The starters themselves stay live, tested projects rather than being forked into template copies.
`node/cli/template/` is **generated and git-ignored** — `prepack` deletes and recreates it from
`node/starter/*` on every pack, so CI builds and tests exactly the code users receive.

`node/cli/overlay/<role>` is the exception, and it is committed source, not generated. It holds the
few files that cannot ship verbatim and is applied *over* the template — currently the `Dockerfile`,
whose in-repo form takes `node/` as its build context so it can resolve the SDK workspace beside it,
where a scaffolded project resolves the SDK from npm. `TemplateExtractor.java` has the same
`overlay/<role>` mechanism for the same reason. The scaffolder also rewrites the starter README's
repo-relative instructions, as `deRepoReadme` does on the Java side.

Two further details in the Node packer that are not obvious:

- **`.env` is excluded, `.env.example` is not.** A developer's starter directory can hold live
  keys. `publish.yaml` re-asserts this against the actual tarball listing rather than trusting the
  filter.
- **`.gitignore` ships undotted** as `template/gitignore`, and the CLI renames it back when it
  scaffolds. npm drops a nested `.gitignore` from a tarball; without the workaround every
  scaffolded project would commit its own `.env` on the first `git add -A`.

  `usdt-pay-init.jar` carries it undotted too, for its own reason: `**/.gitignore` is an Ant
  default exclude, so no Gradle copy will take the file — not even a `from()` that names it
  directly. `cli/build.gradle.kts` copies it by hand in `processResources` and
  `TemplateExtractor.restoreGitignore` puts the dot back. Removing the exclude globally in
  `settings.gradle.kts` is the obvious alternative and was the previous approach; Gradle 9 sees
  the mutated defaults mid-build in a reused daemon and fails the next Copy task in the build
  with "Cannot change default excludes during the build".

The generated `.env` is written from `.env.example` by filling the empty `PRIVATE_KEY=` line — the
same contract, and the same line-anchored substitution, as `EnvFileWriter.java`. The keypair is
derived through the SDK's own `publicKeyFromPrivateKey`, mirroring `KeyGenerator.java` going
through `Signer`: a key generated here has to be one the runtime accepts.

---

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
   2. **Check both npm packages exist**, the same bootstrap gate `publish.yaml`'s `preflight`
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
   4. **Bump.** Node via `npm version … --no-workspaces-update -w sdk -w cli -w starter/issuer`,
      then `npm pkg set` for the two SDK pins, then `npm install --package-lock-only`. Java via
      `sed` on `gradle.properties`. Nothing hand-edits the lockfile.
   5. **Validate** every site in the table above, lockfile entries included. Any mismatch fails
      *before* the push.
   6. **Commit, tag, push** with plain git and `--allow-empty` / `--atomic` (see below).
   7. **Create the GitHub Release**, idempotently.

### Two flags that look cosmetic and are not

- **`--no-workspaces-update`** on `npm version`. Without it npm does an implicit workspace reify.
  At that moment `sdk` is already at the new version while `cli` and the starter still pin `^OLD`,
  so those workspace links no longer satisfy their pins and npm goes to the registry — which may
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
          /-------------------+-------------------\
         /                    |                    \
publish-node-sdk    publish-node-starter    publish-java
```

Nothing publishes until everything builds and the shared **`preflight`** job passes.

`preflight` checks *both* registries' prerequisites in one place:

- the repository is public (`gh api repos/$GITHUB_REPOSITORY --jq .private` = `false`, which npm
  provenance requires);
- `vars.OSSRH_USERNAME` / `secrets.OSSRH_PASSWORD` / `secrets.GPG_PRIVATE_KEY` are all non-empty;
- both npm packages already exist on the registry — a trusted publisher can only be attached to a
  package that exists, so this is the [bootstrap](#before-the-first-release) having happened.
  Necessary, not sufficient: it cannot see whether the trusted publisher is actually configured,
  and nothing here can. A misconfigured one surfaces as a failed publish on that package's job
  alone — before the upload, so nothing is spent.

That it is *shared* is the point. Per-job preflights would let `publish-java` spend the Maven
Central version while `publish-node` was still failing its visibility check — and a spent Central
version cannot be un-spent. A prerequisite missing for either registry now stops both.

### The npm packages publish from separate jobs

`publish-node-sdk` and `publish-node-starter`, as provider-sdk's two npm jobs are. Not cosmetic:
npm refuses to replace a published version, so if both publishes lived in one job and the second
failed, re-running that job would die on the *first* package's duplicate and never reach the one
that still needed publishing. Split, each is independently re-runnable.

### Both npm jobs

`ubuntu-latest` — npm rejects `--provenance` from self-hosted runners. `id-token: write`, no
`NPM_TOKEN`, and no `registry-url` on `setup-node` (that would wire up a `NODE_AUTH_TOKEN` path
that must not exist). Auth is entirely the package's trusted publisher.

Node 22 ships npm 10.x, which has no OIDC support, so the job installs `npm@^11.5.1` and asserts
the result. Pinned to the 11 major rather than `@latest`: a future npm 12 could raise its own Node
floor above 22 and break the job on a day nobody touched the repo.

Then each does: versions-match-tag, `npm ci` (the lockfile is at `node/`, not in the package
dirs), build + typecheck, assert the package is the name it should be and not `private`,
`npm pack --dry-run`, publish.

| Job | Package | Directory |
|---|---|---|
| `publish-node-sdk` | `@t-0/usdt-pay-sdk` | `node/sdk` |
| `publish-node-starter` | `@t-0/usdt-pay-starter-ts` | `node/cli` |

`publish-node-starter` also re-checks `node/starter/issuer`'s version *and* its SDK pin, not just
the scaffolder's own: `prepack` copies that directory into the tarball, so without the check a
hand-pushed tag could ship a scaffolder whose template still pins an older SDK. Its pack step then
asserts the real tarball listing carries `template/.env.example` and nothing credential-shaped —
`.env.local`, `.npmrc`, `*.pem` and friends, not merely an exact `.env`.

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

2. ~~**Apply the infra change** that creates `OSSRH_USERNAME` / `OSSRH_PASSWORD` /
   `GPG_PRIVATE_KEY`.~~ Done — [backend#1404](https://github.com/t-0-network/backend/pull/1404) is
   merged and applied to the **prod** stack.

3. **Bootstrap both npm packages.** A trusted publisher can only be configured on a package that
   already exists, and neither `@t-0/usdt-pay-sdk` nor `@t-0/usdt-pay-starter-ts` does. Both
   `release.yaml` and `publish.yaml` refuse to run until they do, so this step gates the first
   release entirely.

   For **each** of the two, a `@t-0` maintainer — with explicit authorization — publishes one
   interactive 2FA-protected prerelease under a non-`latest` dist-tag, which leaves `0.1.0` free
   for the automated release. From `node/`:

   ```bash
   # Local and uncommitted — this version number must never reach a commit.
   npm version 0.1.0-bootstrap.0 --no-git-tag-version --no-workspaces-update -w sdk
   npm publish -w sdk --tag bootstrap --access public
   git checkout -- sdk/package.json
   ```

   The scaffolder is the same, with `-w cli`. Packing it runs its `prepack` — check the file list
   it prints and confirm it carries `template/<role>/.env.example` and no `template/<role>/.env`.
   Its `^0.1.0` SDK pin dangles until `0.1.0` lands; harmless under a non-`latest` dist-tag.

   Then attach each package's trusted publisher. From the CLI (npm >= 11.15, as the 2FA
   maintainer):

   ```bash
   npm trust github @t-0/usdt-pay-sdk         --repo t-0-network/usdt-pay-sdk --file publish.yaml --allow-publish
   npm trust github @t-0/usdt-pay-starter-ts  --repo t-0-network/usdt-pay-sdk --file publish.yaml --allow-publish
   ```

   or in the package's web settings, with the same values:

   | Field | Value |
   |---|---|
   | Organization | `t-0-network` |
   | Repository | `usdt-pay-sdk` |
   | Workflow filename | `publish.yaml` |
   | Allowed action | `npm publish` |
   | Environment | *(blank)* |

   The same workflow publishes both, so both trusted publishers name `publish.yaml`. Verify one
   OIDC run before revoking any token-based publish rights.

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

The three publish jobs are independent — one failing does not stop the others — so a partial
release across registries is possible in either direction: npm published and Central not, or the
reverse. Neither is worse than the other to sit in, and both recover the same way, with *Re-run
failed jobs* at the same version.

**Re-running after a partial success:** use *Re-run failed jobs*, never *Re-run all jobs* — the
latter re-enters a publish that already succeeded and fails on the duplicate. Because each npm
package has its own job, re-running only the failed one is safe.

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

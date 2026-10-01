# adaa-automation

Black-box end-to-end UI automation, written in Java with Playwright and TestNG.

## What this repository is

This is a **standalone automation repository**. It is not part of the application it
tests, and it does not depend on it in any way:

- no reference to the application's solution, projects, assemblies or packages
- no copied application source
- no database connection, no SQL, no ORM — the application's data is reached only the way
  a user reaches it
- nothing environment-specific is committed; there is not a single URL, account or
  password in this repository

The suite behaves like any external QA client: it opens a browser, signs in, clicks
things, and checks what it sees. It talks to the application over HTTP only, and only
through the browser.

The practical consequence is that this repository can be cloned, built and run by someone
who has never seen the application's source code, and it keeps working when that source
changes — as long as the application still behaves the same way for a user.

## What it covers today

| Test | Group | Checks |
|---|---|---|
| `LoginTests.loginPageRenders` | smoke | The sign-in form renders and is usable |
| `LoginTests.invalidCredentialsAreRejected` | smoke | Credentials that are not real are refused |
| `LoginTests.validCredentialsCanSignIn` | smoke | The configured account reaches the application |
| `ObjectiveTests.objectiveListLoads` | smoke | The objective list renders rows |
| `ObjectiveTests.searchFiltersObjectives` | smoke | Searching narrows the list |
| `ObjectiveTests.clearingSearchRestoresResults` | smoke | Clearing the search restores it |
| `ObjectiveTests.createObjectiveAndVerifyItPersists` | regression | An objective created through the form appears in the list, and reopening it shows what was saved |
| `ObjectiveTests.editObjectiveDescriptionAndVerifyItPersists` | regression | An edited description survives a reopen |
| `ObjectiveTests.arabicPageRendersRightToLeft` | regression | The Arabic page renders RTL |

**smoke** is the fast set that answers "is this deployment usable at all" — it is what a
post-deployment run should execute. **regression** is the broader set that creates and
edits real records, and is better suited to a nightly or pre-release run.

### Test independence

Every test is self-contained. Each one creates whatever record it needs, with a name
unique to the run (`auto-objective-20260101-143052-7Q2K`), and never asserts anything
about data another test created. That is why "create an objective" and "reopen it and
check the data" are one test rather than two — splitting them would make the second
depend on the first having run, and run first.

Tests can be run in any order, individually, or repeatedly.

## Local setup

### Prerequisites

- **JDK 17** on `PATH` ([Temurin](https://adoptium.net/temurin/releases/?version=17) is a
  good default)
- Nothing else. Maven is downloaded by the wrapper on first use, and Playwright downloads
  its own browsers.

### Configuration

Everything comes from environment variables. Nothing is hard-coded, and nothing is
committed.

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `AUTOMATION_BASE_URL` | **yes** | — | The application under test, e.g. `http://localhost:5225` |
| `AUTOMATION_USERNAME` | for signed-in tests | — | An account on that environment |
| `AUTOMATION_PASSWORD` | for signed-in tests | — | Its password |
| `AUTOMATION_HEADLESS` | no | `true` | `false` to watch the browser |
| `AUTOMATION_TIMEOUT_MS` | no | `15000` | Per-action timeout |
| `AUTOMATION_SLOWMO_MS` | no | `0` | Pause between actions, for debugging |
| `AUTOMATION_LOCALE` | no | `en-US` | Browser locale |
| `AUTOMATION_ENV` | no | `local` | A label for the report header; changes no behaviour |

Each one can also be given as a JVM property with a short name, which takes precedence —
that is what makes `-Dheadless=false` work.

Without `AUTOMATION_BASE_URL` the whole suite **skips** with a message saying so. Without
credentials, the signed-in tests skip. They never pass silently against nothing.

```bash
export AUTOMATION_BASE_URL=http://localhost:5225
export AUTOMATION_USERNAME=qa.automation@example.com
export AUTOMATION_PASSWORD=...        # never commit this
```

> Do not put credentials in a file inside this repository. `.env` files are ignored by
> git, but the safest place for them is your shell profile or your CI secret store.

### Running

```bash
./mvnw clean test                       # everything, headless
./mvnw clean test -Dheadless=false      # watch it run
./mvnw clean test -Dgroups=smoke        # the fast set only
./mvnw clean test -Dgroups=regression   # the broader set only
./mvnw clean test -Dtest=ObjectiveTests # one class
./mvnw clean test -Psuite               # via the explicit testng.xml definition
```

On Windows use `mvnw.cmd` in place of `./mvnw`.

The first run downloads Maven, then Chromium; later runs start immediately.

## How it is put together

```
src/test/java/com/adaa/automation/
├── config/     Config            - every setting, resolved from the environment
├── utils/      PlaywrightManager - one browser per run, one context per test
│               Artifacts         - screenshots, traces and logs for failures
│               TestListener      - the run log and the final counts
│               TestData          - unique names, so no test collides with another
├── fixtures/   BaseTest          - browser lifecycle, artifact capture
│               AuthenticatedTest - the same, already signed in
│               AuthSession       - signs in once per run
├── pages/      LoginPage, ObjectiveListPage, ObjectiveFormPage, ObjectiveDetailsPage
└── tests/      LoginTests, ObjectiveTests
```

**Page Object Model.** Every selector lives in a page object; no test contains one. A
test says what a user does, and the page object knows how that is reached in the DOM —
so when the application's markup changes, one file changes.

**Authentication.** The suite signs in exactly once per run and saves the resulting
session (Playwright's *storage state*) to `target/.auth/`. Every test then starts from a
fresh browser context seeded with that session. This is faster, and it is safer: a
Playwright trace records the value passed to every `fill()`, so a test that types a
password into a traced context would write that password into an artifact that CI then
uploads. The one sign-in happens in its own untraced context, and no other test ever
types a credential.

**Isolation.** One browser per run, but a new `BrowserContext` per test. Cookies, storage
and session all belong to the context, so no test can see another's state.

## GitHub Actions

Two workflows:

| Workflow | Runs on | Does |
|---|---|---|
| `build.yml` | pull requests and pushes to `main` | Compiles the suite. No environment or credentials needed, so a PR is checkable in about a minute. |
| `automation.yml` | `workflow_dispatch`, `repository_dispatch`, nightly schedule | Runs the suite against a deployed environment. |

`automation.yml` resolves its target in this order: a `base_url` input, then the
triggering payload's `base_url`, then the GitHub Environment's `AUTOMATION_BASE_URL`
variable. It runs headless, installs Chromium, and caches both Maven and the browsers.

### Secrets and variables

Configure these on a GitHub **Environment** (`qa`, `staging`, …), so each target's
settings are grouped and can carry its own protection rules:

| Name | Kind |
|---|---|
| `AUTOMATION_BASE_URL` | Environment **variable** (not sensitive) |
| `AUTOMATION_USERNAME` | Environment **secret** |
| `AUTOMATION_PASSWORD` | Environment **secret** |

Use a dedicated QA account, not a personal one. No credential appears in any workflow
file.

### Artifacts

| Artifact | When |
|---|---|
| TestNG and JUnit reports — passed, failed, skipped, duration | always |
| Failure screenshots (full page) | on failure |
| Playwright traces | on failure |
| Browser console output and uncaught page errors | on failure |

Results are also published as a check with per-test annotations, so the run page answers
"did it pass" without downloading anything.

To look at a downloaded trace:

```bash
npx playwright show-trace failure-artifacts/ObjectiveTests.objectiveListLoads.trace.zip
```

It replays the test with a DOM snapshot, the network log and a screenshot at every step.

## Future integration: triggering this suite after a deployment

This repository is deliberately **not** coupled to the application's pipeline. Nothing
here needs to change for the application to be built or deployed, and nothing in the
application's repository needs to know how these tests work.

When you want a deployment to run this suite, add one step to the deploying pipeline,
after its deploy job succeeds. Nothing in this repository changes — `automation.yml`
already listens for it.

```yaml
- name: Trigger E2E automation
  run: |
    curl -sf -X POST \
      -H "Authorization: Bearer ${{ secrets.AUTOMATION_DISPATCH_TOKEN }}" \
      -H "Accept: application/vnd.github+json" \
      https://api.github.com/repos/<org>/adaa-automation/dispatches \
      -d '{"event_type":"adaa-deployed",
           "client_payload":{"environment":"qa",
                             "base_url":"https://qa.example.com",
                             "suite":"smoke"}}'
```

`AUTOMATION_DISPATCH_TOKEN` is a fine-grained personal access token scoped to this
repository with *Contents: read and write*. The built-in `GITHUB_TOKEN` cannot dispatch
into another repository, which is why a token is needed.

Until that step exists, the suite is run from the Actions tab (`workflow_dispatch`) and
nightly on its schedule. Both are fully supported today.

## Adding a test

1. Put the selectors in the page object, never in the test.
2. Extend `AuthenticatedTest` if the test needs to be signed in, `BaseTest` if not.
3. Give it a group: `smoke` if it is fast and critical, `regression` otherwise.
4. Generate any data it needs with `TestData.uniqueName(...)`.
5. Assume nothing about what already exists in the environment.

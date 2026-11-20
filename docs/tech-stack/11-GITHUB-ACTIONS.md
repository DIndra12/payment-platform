# GitHub Actions

**Used In:** CI/CD pipeline for automated build, test, coverage

## What It Is

GitHub Actions is GitHub's built-in automation platform for running workflows on code events (push, pull request, schedule). We use it to automatically build, test, and validate code changes.

## Where & Why We Use It

**Why GitHub Actions?**
- Native GitHub integration (no external services)
- Free for public repos and generous limits on private
- Simple YAML configuration
- Large marketplace of pre-built actions
- Good documentation
- Runs on GitHub-hosted runners (Ubuntu, Windows, macOS)

**Our Workflow:**
```
Push to branch
    ↓
GitHub detects push
    ↓
Runs .github/workflows/ci.yml
    ├─→ Checkout code
    ├─→ Set up Java 21
    ├─→ Build all services (mvn clean package)
    ├─→ Run unit tests (-P unit)
    ├─→ Run integration tests (-P integration, Testcontainers)
    ├─→ Run coverage enforcement (-P coverage)
    ├─→ Build Docker images
    └─→ Report results (success/failure)
```

## GitHub Actions Workflow File

**`.github/workflows/ci.yml`:**
```yaml
name: CI Build & Test

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_PASSWORD: postgres
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 5432:5432
      
      kafka:
        image: confluentinc/cp-kafka:7.5.0
        env:
          KAFKA_BOOTSTRAP_SERVERS: kafka:29092
        options: >-
          --health-cmd kafka-broker-api-versions.sh
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
        ports:
          - 9092:9092
    
    steps:
      - uses: actions/checkout@v3
        with:
          fetch-depth: 0  # For proper versioning
      
      - name: Set up Java 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      
      - name: Build all services
        run: mvn clean package -DskipTests
      
      - name: Run unit tests
        run: mvn -P unit test
      
      - name: Run integration tests
        run: mvn -P integration test
      
      - name: Run coverage enforcement
        run: mvn -P coverage verify
      
      - name: Upload coverage reports
        uses: codecov/codecov-action@v3
        with:
          files: ./payment-service/target/site/jacoco/jacoco.xml
      
      - name: Build Docker images
        run: |
          docker build -t payment-service:latest ./payment-service
          docker build -t account-service:latest ./account-service
      
      - name: Run security scan (Snyk)
        uses: snyk/actions/setup@master
        with:
          snyk-token: ${{ secrets.SNYK_TOKEN }}
      
      - name: Publish test results
        uses: actions/upload-artifact@v3
        if: always()
        with:
          name: test-results
          path: '**/target/surefire-reports/**'

  code-quality:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Run CodeQL analysis
        uses: github/codeql-action/init@v2
        with:
          languages: java
      
      - name: Perform CodeQL analysis
        uses: github/codeql-action/analyze@v2
```

## Key Concepts

**Triggers (when workflow runs):**
- `push` — on code push
- `pull_request` — on PR creation/update
- `schedule` — on cron schedule (nightly, weekly)
- `workflow_dispatch` — manual trigger

**Jobs:**
- Run in parallel by default
- Can depend on other jobs (`needs: build`)
- Each job runs in a fresh container

**Steps:**
- Run sequentially within a job
- Can be actions (pre-built) or shell commands
- Fail fast (stop on first failure)

**Secrets:**
- Stored encrypted in GitHub
- Used for API tokens, credentials
- Access: `${{ secrets.SECRET_NAME }}`

## Common Actions We Use

| Action | Purpose |
| --- | --- |
| `actions/checkout` | Clone repo code |
| `actions/setup-java` | Install Java runtime |
| `actions/upload-artifact` | Store build outputs |
| `codecov/codecov-action` | Upload coverage reports |
| `docker/login-action` | Login to Docker registry |
| `docker/build-push-action` | Build and push images |
| `snyk/actions` | Security scanning |

## How to Improve & Learn

**Short Term (1-2 weeks):**
1. Understand workflow syntax
   - Read GitHub Actions documentation
   - Create simple test workflow
   - Debug with `actions/setup-problem-matchers`

2. Optimize build speed
   - Cache Maven dependencies (`.m2` directory)
   - Parallel jobs for independent tasks
   - Use `needs` for job dependencies

3. Add notifications
   ```yaml
   - name: Notify Slack on failure
     if: failure()
     uses: slackapi/slack-github-action@v1
     with:
       webhook-url: ${{ secrets.SLACK_WEBHOOK }}
       payload: |
         {"text": "Build failed: ${{ github.run_id }}"}
   ```

**Medium Term (1-2 months):**
1. Advanced workflows
   - Matrix builds (multiple Java versions)
   - Conditional steps (`if: success()`)
   - Reusable workflows

2. Release automation
   ```yaml
   release:
     runs-on: ubuntu-latest
     needs: build
     if: github.ref == 'refs/heads/main'
     steps:
       - uses: actions/checkout@v3
       - name: Create release
         uses: softprops/action-gh-release@v1
         with:
           draft: true
   ```

3. Performance monitoring
   - Track build time trends
   - Alert on regressions
   - Optimize slow steps

**Long Term (3+ months):**
1. Multi-environment deployments
   - Dev → Test → Staging → Production
   - Approval gates
   - Rollback automation

2. Custom actions
   - Create internal action for company standards
   - Reuse across projects
   - Share in GitHub Marketplace

3. Advanced CI patterns
   - Feature branch environments
   - Performance testing in CI
   - Synthetic monitoring

## Workflow File Structure

**Best practices:**
1. Keep workflows under 100 lines (extract to actions)
2. Use descriptive job/step names
3. Add `if: always()` to upload artifacts even on failure
4. Use caching for dependencies (Maven, npm)
5. Run security scans (CodeQL, Snyk)
6. Notify on failures (Slack, email)

## Example: Matrix Builds

```yaml
jobs:
  test:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        java-version: [17, 21]
        postgres-version: [15, 16]
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: ${{ matrix.java-version }}
      - run: mvn clean test
```

## Troubleshooting

| Issue | Cause | Fix |
| --- | --- | --- |
| "Java not found" | setup-java not running | Ensure it comes before maven/build steps |
| Builds take too long | No caching | Add `cache: maven` to setup-java |
| Test fails in CI but passes locally | Environment difference | Check env vars, services |
| Out of disk space | Large artifacts | Delete old runs: Settings → Actions |

## Monitoring & Alerts

**GitHub provides:**
- Workflow status badge (add to README)
- Email notifications on failure
- Slack integration via GitHub App
- Status checks on PRs (block merge if failing)

## Learning Resources

- **Official:** https://docs.github.com/en/actions
- **Quickstart:** https://docs.github.com/en/actions/quickstart
- **Workflow Syntax:** https://docs.github.com/en/actions/using-workflows/workflow-syntax-for-github-actions
- **Actions Marketplace:** https://github.com/marketplace?type=actions
- **Examples:** https://github.com/actions/starter-workflows

## Next Steps

- [ ] Add matrix builds (test multiple Java versions)
- [ ] Enable CodeQL security scanning
- [ ] Add Slack notifications for failures
- [ ] Optimize build cache strategy
- [ ] Add performance regression detection
- [ ] Create status badge for README
- [ ] Set up automated releases
- [ ] Add deployment workflow (CD)

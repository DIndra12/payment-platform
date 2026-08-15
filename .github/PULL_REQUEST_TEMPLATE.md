## Description

Please include a short description of the change and the motivation.

## Branching
- Create a feature branch named: feature/<short-description>-<ticket>
- Push branch and open a Pull Request targeting `main`.

## Reviewers
- This repo requires review from CODEOWNERS before merging. Change the reviewers list if needed.

## CI
- The PR will run the following checks automatically:
  - Code scanning (CodeQL)
  - Unit tests (JUnit/Surefire)
  - Integration tests (Failsafe/Testcontainers)
  - Acceptance tests (profile: acceptance)

Do not merge until all checks pass and required reviewers approve.

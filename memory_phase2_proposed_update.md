## Proposed MEMORY.md Block

```md
# Task Group: C:\Users\31283\Documents\Ciphershell / full-repo refactor and fail-closed hardening
scope: review and refactor the local Ciphershell checkout from repo bootstrap through Windows buildability, then prioritize fail-closed security/runtime hardening when PE or VM paths are unsafe
applies_to: cwd=C:\Users\31283\Documents\Ciphershell; reuse_rule=safe for this checkout and similar Windows C++ repo-maintenance/security-hardening work when the same repo structure or protection pipeline is in play, but re-check branch state, toolchain availability, and whether unsafe modules are still intentionally disabled

## Task 1: Full-repo orientation, bootstrap from blank checkout, and generic Windows build cleanup, success

### rollout_summary_files

- rollout_summaries/2026-07-11T17-17-00-TIbh-ciphershell_full_repo_refactor_and_fail_closed_hardening.md (cwd=\\?\C:\Users\31283\Documents\Ciphershell, rollout_path=C:\Users\31283\.codex\archived_sessions\rollout-2026-07-12T01-17-05-019f522e-b5a9-7330-b88d-482382428be3.jsonl, updated_at=2026-07-11T19:25:44+00:00, thread_id=019f522e-b5a9-7330-b88d-482382428be3, empty local repo was fetched from `origin/main`, made buildable on Windows, and cleaned up before deeper hardening)

### keywords

- Ciphershell, Anfyya/Ciphershell, VS2022 BuildTools, bundled CMake, NASM, build_win.bat, UTF-8, Chinese replies, build_review, empty local checkout

## Task 2: High-risk PE/VM fail-closed hardening and replacement PR publish flow, success

### rollout_summary_files

- rollout_summaries/2026-07-11T17-17-00-TIbh-ciphershell_full_repo_refactor_and_fail_closed_hardening.md (cwd=\\?\C:\Users\31283\Documents\Ciphershell, rollout_path=C:\Users\31283\.codex\archived_sessions\rollout-2026-07-12T01-17-05-019f522e-b5a9-7330-b88d-482382428be3.jsonl, updated_at=2026-07-11T19:25:44+00:00, thread_id=019f522e-b5a9-7330-b88d-482382428be3, unsafe protection paths were made fail closed, tests/builds stayed green, and the final work moved to a new draft PR after PR #1 had already merged)

### keywords

- fail-closed, PEParser, PEEmitter, CapabilityChecker, SignatureEliminator, SafeSEH, IMAGE_DIRECTORY_ENTRY_SECURITY, TranslateCall, external native CALL, gh pr create, PR #2

## User preferences

- when the user asks `审查并重构 ... 仓库中的代码。先问我要重点关注代码库的哪一部分。`, ask for focus before reading/editing if the target area is not yet specified [Task 1]
- when the user answers `全部`, default to broad repo-wide review instead of narrowing prematurely [Task 1]
- when the user says `使用中文回复我`, keep replies in Chinese for this repo/workflow unless they switch languages again [Task 1][Task 2]
- when a repo-wide review surfaces unsafe runtime/protection behavior, treat `repair or fail closed` as higher priority than cosmetic refactors or style cleanup [Task 2]

## Reusable knowledge

- This repo's real Windows build path was VS2022 BuildTools plus the bundled `cmake.exe` under `CommonExtensions\Microsoft\CMake\CMake\bin`, with NASM installed separately; a clean Release build succeeded only after using that toolchain explicitly [Task 1]
- The local checkout initially contained only `.git`; the successful bootstrap was `git remote add origin https://github.com/Anfyya/Ciphershell.git`, fetch `origin/main`, then build from the fetched tree rather than assuming files were already present [Task 1]
- `build_win.bat` was tied to an old machine path and needed to be made self-contained; the repo also contains Chinese docs/source, so UTF-8-safe reads/scans are part of the safe default for this project [Task 1]
- `tools/decryptor.cpp` behaved like unsupported diagnostic code rather than a production decryptor, so it should not be treated as evidence that a real decrypt flow exists [Task 1]
- The durable hardening direction was fail-closed: unfinished modules such as `section_encryption`, `string_encryption`, `import_protection`, `control_flow`, `flattening`, and `bogus` were disabled by default and explicitly rejected by `CapabilityChecker` rather than being silently enabled by presets [Task 2]
- `SignatureEliminator` permission normalization conflicted with VM metadata/data sections that must stay read-only; removing that normalization was necessary for deterministic final checks [Task 2]
- `PEParser` needed deep bounds validation across import/export/relocation/resource/TLS/exception/load-config/debug/delay-import/security directories, with special handling for `IMAGE_DIRECTORY_ENTRY_SECURITY` because its `VirtualAddress` is a file offset, not an RVA [Task 2]
- `PEEmitter` must update file-offset directories when headers or overlays move, and `TranslateCall` should not guess native-call stack arguments; unsupported external or indirect native/import CALLs now need to fail closed until ABI recovery is proven [Task 2]
- `FunctionDiscovery` can surface x86 SafeSEH handler RVAs and the VM gating should consult that metadata before allowing virtualization on code with unproven handler ownership [Task 2]
- The final validated publish state was commit `076bec0` on branch `codex/full-repo-refactor`, with `build_win.bat Release`, `git diff --check`, and the UTF-8/mojibake scan all passing before the draft PR was created at `https://github.com/Anfyya/Ciphershell/pull/2` [Task 2]

## Failures and how to do differently

- Symptom: the first build fails because `cmake` is missing from PATH -> cause: this machine relied on VS2022 BuildTools without a global CMake install -> fix: call the bundled Visual Studio `cmake.exe` directly instead of assuming PATH is ready [Task 1]
- Symptom: `winget install --id NASM.NASM --exact --scope user` fails with `找不到适用的安装程序` -> cause: the scoped installer route does not resolve on this machine -> fix: retry the NASM install without `--scope user` [Task 1]
- Symptom: repo review drifts into generic cleanup while the real risk is unfinished protection/runtime behavior -> cause: broad-scope refactor work can hide the highest-severity semantic issues -> fix: pivot to parser/runtime/capability safety first and leave lower-risk cleanup secondary [Task 2]
- Symptom: updating the existing PR through the connector fails with `403 Resource not accessible by integration` -> cause: the target PR was cross-fork and already merged -> fix: use authenticated `gh pr edit` / `gh pr create` for the final publish flow and open a replacement draft PR when the merged PR no longer reflects the real change set [Task 2]
- Symptom: translator hardening still leaves stale helper logic behind -> cause: incremental safety patches removed behavior in multiple passes -> fix: do a second cleanup pass in `translator.cpp` after compile/test recovery to remove leftover call-argument inference helpers [Task 2]
```

## Proposed memory_summary.md Changes

- Update `## User Profile` to add `C++ repo hardening` to the recurring work list.
- Add to `## User preferences`:
  - `When a repo-wide review starts broad and the user chooses \`全部\`, do the whole checkout review and pivot to \`repair or fail closed\` if unsafe runtime/security behavior turns up.`
- Add this scope at the top of `## What's in Memory`:

```md
### C:\Users\31283\Documents\Ciphershell

#### 2026-07-11

- Ciphershell full-repo refactor and fail-closed hardening: Ciphershell, VS2022 BuildTools, bundled CMake, NASM, PEParser, CapabilityChecker, SafeSEH, fail-closed, gh pr create
  - desc: Search here first for `cwd=C:\Users\31283\Documents\Ciphershell` when the task is a repo-wide review/refactor, Windows build bootstrap from a sparse checkout, or PE/VM safety hardening in the local Ciphershell repo.
  - learnings: Bootstrap from `origin/main`, use the VS2022 bundled `cmake.exe` plus NASM, keep reads UTF-8-safe, and prefer disabling unsafe protection paths by default over shipping partially proven runtime behavior.
```

## Blocker

- Direct writes to `C:\Users\31283\.codex\memories\` were denied in this run, so the proposed consolidation could not be applied in place.

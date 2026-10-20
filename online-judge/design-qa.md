# Design QA — Student Problem Workbench

- source visual truth path: `C:\Users\Lenovo\AppData\Local\Temp\codex-clipboard-22561031-1621-48f2-b5b5-98c1dc356b6e.png`
- prior implementation reference: `C:\Users\Lenovo\AppData\Local\Temp\codex-clipboard-104f3ba0-3bac-4a2b-85c2-af577dc8aaf6.png`
- consistency reference — problem page: `C:\Users\Lenovo\AppData\Local\Temp\codex-clipboard-f0cbcd46-1169-4218-abda-d75fc5625802.png`
- consistency reference — assignment overview: `C:\Users\Lenovo\AppData\Local\Temp\codex-clipboard-98602bd7-aebd-4881-8fa3-9ec6c3df5123.png`
- edge-to-edge reference — problem page: `C:\Users\Lenovo\AppData\Local\Temp\codex-clipboard-e91d57d1-9da9-4b45-94c2-e5ca276b723e.png`
- edge-to-edge reference — assignment overview: `C:\Users\Lenovo\AppData\Local\Temp\codex-clipboard-88f922cc-b182-4724-9483-575fc25d58ef.png`
- shared chrome reference — assignment overview: `C:\Users\Lenovo\AppData\Local\Temp\codex-clipboard-1bda7f13-d1a7-471c-9049-190614c9801d.png`
- shared chrome issue markup — problem page: `C:\Users\Lenovo\AppData\Local\Temp\codex-clipboard-396df656-e206-42b3-8cd1-5010c664690c.png`
- implementation screenshot path: `D:\WenCode\Code\online-judge\output\design-qa\student-problem-workbench.png`
- final unified implementation screenshot path: `D:\WenCode\Code\online-judge\output\design-qa\student-problem-unified-final.png`
- final full-screen implementation screenshot path: `D:\WenCode\Code\online-judge\output\design-qa\student-problem-fullscreen.png`
- final chrome-alignment screenshot path: `D:\WenCode\Code\online-judge\output\design-qa\student-problem-chrome-final.png`
- shared-component overview screenshot path: `D:\WenCode\Code\online-judge\output\design-qa\student-assignment-shared-chrome-2048.png`
- shared-component problem screenshot path: `D:\WenCode\Code\online-judge\output\design-qa\student-problem-shared-chrome-2048.png`
- shared-component comparison path: `D:\WenCode\Code\online-judge\output\design-qa\student-shared-chrome-current-comparison.png`
- collapsed-state screenshot path: `D:\WenCode\Code\online-judge\output\design-qa\student-problem-workbench-collapsed.png`
- mobile screenshot path: `D:\WenCode\Code\online-judge\output\design-qa\student-problem-workbench-mobile.png`
- combined comparison path: `D:\WenCode\Code\online-judge\output\design-qa\student-problem-comparison.png`
- viewport: 1600 x 1000 desktop; 390 x 844 mobile
- state: signed-in student, first classroom task selected, editor open with starter code

## Full-view comparison evidence

The combined comparison places the selected reference and implementation in one frame. Both use a persistent primary rail, a secondary task list, and one full-height working canvas split between the statement and code editor. The final consistency pass also verifies that the problem page now uses the exact shared assignment header component from the overview page: back action, assignment title, class, deadline, and student identity align in the same order and at the same height.

## Focused interaction evidence

- The desktop capture shows the 50 / 50 default statement-editor split and visible drag handle.
- The collapsed-state capture shows the statement expanding across the canvas while a narrow, labeled code restore control remains available.
- The mobile capture shows primary navigation as a horizontal rail and the statement/editor stacking without horizontal overflow.
- The automated browser test drags the separator by 140 px and verifies the statement grows while the editor shrinks; it also collapses and restores the editor.

## Required fidelity surfaces

- Fonts and typography: the existing Chinese system font stack is retained. Page titles, problem headings, metadata, task labels, and editor chrome preserve a clear hierarchy without clipped text.
- Spacing and layout rhythm: the outer rail, task sidebar, statement, separator, and editor occupy one continuous full-height canvas. Borders replace the earlier detached-card gaps, matching the reference's denser workspace.
- Colors and visual tokens: the existing teal brand, pale active state, neutral dividers, dark code toolbar, and semantic task states closely match the reference direction.
- Image quality and asset fidelity: the reference contains no photographic or illustrative assets. Navigation, resize, collapse, and editor controls use the project's installed Lucide icon library consistently.
- Copy and content: navigation labels are concise; assignment title, task list, problem content, limits, language, starter code, and submission actions remain driven by application data.

## Findings

No actionable P0, P1, or P2 differences remain.

Accepted product constraints:

- The production platform header remains above the workbench so student/teacher switching, identity, language, and theme controls stay consistent across the app.
- The secondary sidebar lists this assignment's actual programming tasks rather than the reference site's mixed question-type categories.

## Comparison history

1. Initial implementation retained three detached cards and had no persistent primary rail, resize control, or collapse state. These were P1 structure and interaction mismatches.
2. The workbench was rebuilt with a persistent rail, secondary task list, border-based continuous canvas, resizable statement/editor split, and collapsible editor.
3. First automated resize check exposed a P1 layout constraint: later wide-screen CSS limited the working canvas, leaving the statement pinned at its minimum width. Final cascade rules now give the workbench the full viewport width; post-fix drag assertions pass.
4. Desktop, collapsed, and mobile states were recaptured. No clipping, overlap, missing primary action, or horizontal overflow remains in the tested states.
5. A consistency pass found a P1 shell mismatch: the overview had an assignment context header while the problem workbench started directly below the global header. Both pages now reuse `StudentAssignmentHeader`; the duplicate inner “返回学生端” action was removed and the workbench height was recalibrated for the added 72 px header. The final unified capture confirms matching hierarchy and alignment.
6. A final P1 canvas mismatch remained at wide viewports: the problem page inherited the general `main-shell` gutter, leaving 38 px of blank space on both sides and 21 px above the assignment header at 1600 px. The problem workbench now uses an edge-to-edge 100vw canvas and offsets the generic shell padding. Browser assertions confirm x = 0, width equals the viewport, and the assignment header starts immediately below the global header.
7. The final chrome pass removed the last subtle mismatch: problem-page rail icons were 21 px while the other assignment pages used 22 px. The problem page now uses the shared workspace and rail classes plus one shared dimension token set: 72 px assignment header, 84 px rail, 72 px rail items, identical padding and gap, and 22 px icons. Automated browser measurements compare both pages directly and require exact equality.
8. Follow-up comparison found that matching class names was still not sufficient: the problem route retained problem-specific header and rail wrappers, and the wide-screen `main-shell` rule added 3.5 px above the problem canvas at 2048 px. The problem route now renders the same `StudentAssignmentHeader` and `StudentAssignmentNavigation` components as overview, submissions, and ranking, with no assignment-route `problem-*` chrome classes. Wide-screen shell exclusions remove the padding compensation hack. The 2048 x 1087 side-by-side capture confirms identical header and primary-rail geometry.

## Interaction and runtime checks

- Task switching links remain functional.
- Overview, task, submission, and ranking navigation remain functional.
- Drag separator supports pointer input and keyboard left/right adjustment.
- Code panel collapses and restores without losing source state.
- Language selection, editor, submit, and reset controls remain present.
- Browser console errors and page errors: none in the targeted workbench test.
- Automated checks passed: TypeScript typecheck, production build, 28 visual-smoke checks, problem workbench test, assignment overview test, six signed-in home tests, and guest layout test.

final result: passed

---

# Design QA — Read-only Problem Detail

- source issue capture: `C:\Users\Lenovo\AppData\Local\Temp\codex-clipboard-70ac7c30-5a4d-404f-b0c0-f2989b9f4320.png`
- implementation capture: `output/playwright/teacher-management-problems-desktop-light.png`
- responsive captures: `output/playwright/teacher-management-problems-mobile-light.png`, `output/playwright/teacher-management-problems-tablet-light.png`
- combined comparison: `output/design-qa-readonly-problem-comparison.png`
- comparison state: published public problem, read-only teacher view, light theme
- scope: the right-side read-only problem detail only; catalog navigation, draft editing, and version actions remain unchanged

## Visual checks

- The title, scope, version state, and revision action keep their existing hierarchy.
- The former centered empty state is replaced by a compact read-only notice followed immediately by useful content.
- Difficulty, time, memory, and public sample count form one scannable summary row.
- The complete statement, public input/output samples, teaching metadata, AI guidance, and optional starter code are available without enabling editing.
- Hidden judge data is summarized by count and is not exposed in the read-only teacher view.
- Desktop uses the full reading canvas; tablet and mobile stack without horizontal overflow.
- Existing typography, teal brand color, borders, radii, and Lucide icon treatment are reused.

## Interaction and permission checks

- Published, frozen, review-pending, rejected, and archived versions use the read-only detail.
- Draft versions continue to open the existing editable task editor.
- The shared published revision action still creates a new version; the selected version itself remains immutable.
- The targeted browser suite verifies that no textarea or embedded editor appears in the published view.

## Findings

- P0: none
- P1: none
- P2: none after replacing the blank empty-state canvas with the complete problem reader

## Automated checks

- TypeScript typecheck: passed
- Production build: passed
- Targeted browser smoke: 74 checks passed across mobile, tablet, and desktop in light and dark themes
- Horizontal overflow: none at all tested widths

final result: passed

---

# Design QA — Teacher Class Roster Workspace

- reference: `C:\Users\Lenovo\.codex\generated_images\01a00477-a41a-72b3-8b1d-6d8adfb8e704\exec-a1258a50-83a1-4c87-853f-daa17fb1b2d8.png`
- implementation capture: `output/playwright/teacher-management-desktop-light.png`
- combined comparison: `output/design-qa-class-roster-comparison.png`
- comparison viewport: 1680 × 900, light theme, roster tab selected
- scope: the inner class selector and roster workspace only; the global header, permanent management sidebar, and page intro remain unchanged

## Visual checks

- The class selector stays narrow while the roster canvas carries the primary visual weight.
- The class title, summary pills, and primary import action align in one compact header.
- The default screen exposes the roster rather than the long import workflow.
- The roster uses a single table-like row with clear student, number, status, and action columns.
- Borders, radii, typography, brand color, and icon treatment reuse the existing product design system.
- Tablet and mobile layouts stack cleanly without horizontal overflow; mobile actions retain touch-safe sizing.

## Interaction checks

- `导入或更新名单` opens the import workflow.
- `学生名单`, `导入记录`, and `班级设置` switch panels and expose the expected content.
- Returning to `学生名单` restores the compact default state.
- Class selection resets the workspace to the roster tab.

## Findings

- P0: none
- P1: none
- P2: none after widening the scoped workbench and isolating class-row styles from the legacy global minimum-width rule

final result: passed

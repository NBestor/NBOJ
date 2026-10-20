# Spec: Campus UI Simplification Upgrade

## Why
The platform is used inside school classrooms by teachers and students. The UI must behave like a clear teaching tool: fewer explanations, stronger hierarchy, restrained colors, and faster access to the next action.

## Goals
- Keep each screen focused on one primary job.
- Remove AI/product-style panels from student and classroom primary flows.
- Use framed status text consistently without turning pages into badge noise.
- Improve tablet and phone layouts so core actions appear before secondary details.
- Keep light and dark themes visually paired and readable.

## Non-goals
- Do not change backend APIs, database schema, judging logic, or learning diagnosis logic.
- Do not create a marketing-style homepage.
- Do not add decorative copy or visual effects without interaction purpose.

## Screens
- `/app`: role entry
- `/app/student`: student invite, identity, assignment, submission result
- `/app/teacher`: classroom process board
- `/app/teacher-management`: class, import, system status
- `/app/task-editor`: problem authoring
- `/app/problem/:id`: problem, editor, submission result
- `/app/class-overview`: class-level task overview

## Tasks
- [ ] Remove low-frequency analysis panels from student and teacher primary pages.
- [ ] Simplify editor and problem pages with progressive disclosure.
- [ ] Reduce status density on overview and teacher pages.
- [ ] Unify page shell, cards, buttons, lists, status pills, and mobile touch sizing.
- [ ] Remove stale CSS for deleted modules.
- [ ] Verify typecheck, build, desktop/tablet/phone layout, light/dark theme, overflow, and touch targets.

## Acceptance
- [ ] Student page only shows invite, identity, assignment, and submission state.
- [ ] Teacher page only shows assignment selection, classroom KPIs, high-frequency issues, student process, and actions.
- [ ] Task editor mobile flow is shorter and uses collapsed advanced sections.
- [ ] Problem page keeps history and assignment record secondary.
- [ ] No route has horizontal overflow at 390px, 820px, or 1440px.
- [ ] Visible touch controls are at least 44px high on tablet and phone.
- [ ] `npm run typecheck` and `npm run build` pass.

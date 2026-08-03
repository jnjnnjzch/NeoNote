from pathlib import Path

path = Path(__file__).resolve().parents[1] / "app/src/main/java/com/neonote/engine/InputRouter.kt"
text = path.read_text(encoding="utf-8")
old = '''                pending.hasExceededTapSlop ->
                    InputRouteResult(canvas = canvas, action = InputAction.EndInteraction)
                event.targetObjectId != null ->
                    InputRouteResult(canvas = canvas, action = InputAction.FocusExisting(event.targetObjectId))
                else -> InputRouteResult(canvas = canvas, action = InputAction.Ignored)'''
new = '''                pending.hasExceededTapSlop ->
                    InputRouteResult(canvas = canvas, action = InputAction.EndInteraction)
                else -> InputRouteResult(canvas = canvas, action = InputAction.Ignored)'''
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise RuntimeError("Navigation tap branch not found")
path.write_text(text, encoding="utf-8")
print("Navigation taps no longer activate text objects")

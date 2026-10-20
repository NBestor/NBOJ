import { useEffect, useMemo, useRef } from "react";
import CodeMirror from "@uiw/react-codemirror";
import { StateField } from "@codemirror/state";
import { Decoration, EditorView, type DecorationSet } from "@codemirror/view";
import { cpp } from "@codemirror/lang-cpp";
import { python } from "@codemirror/lang-python";
import { oneDark } from "@codemirror/theme-one-dark";
import { contestLanguageById } from "./languages";

type CodeEditorProps = {
  languageId: number;
  sourceCode: string;
  onChange: (value: string) => void;
  highlightLine?: number | null;
  highlightNonce?: number;
  minHeight?: string;
  className?: string;
};

function lineHighlightExtension(line?: number | null) {
  if (!line || line < 1) {
    return [];
  }
  const field = StateField.define<DecorationSet>({
    create(state) {
      const target = Math.min(line, state.doc.lines);
      return Decoration.set([Decoration.line({ attributes: { class: "cm-line-evidence-highlight" } }).range(state.doc.line(target).from)]);
    },
    update(value) {
      return value;
    },
    provide: fieldRef => EditorView.decorations.from(fieldRef)
  });
  return [
    field,
    EditorView.baseTheme({
      ".cm-line-evidence-highlight": {
        backgroundColor: "rgba(45, 212, 191, 0.2)",
        boxShadow: "inset 4px 0 0 #2dd4bf"
      }
    })
  ];
}

export default function CodeEditor({ languageId, sourceCode, onChange, highlightLine, highlightNonce = 0, minHeight = "300px", className }: CodeEditorProps) {
  const viewRef = useRef<EditorView | null>(null);
  const extensions = useMemo(
    () => [
      contestLanguageById(languageId).editorKind === "cpp" ? cpp() : python(),
      ...lineHighlightExtension(highlightLine)
    ],
    [highlightLine, languageId]
  );
  const theme = document.documentElement.dataset.theme === "dark" ? oneDark : undefined;

  useEffect(() => {
    const view = viewRef.current;
    if (!view || !highlightLine || highlightLine < 1) {
      return;
    }
    const target = Math.min(highlightLine, view.state.doc.lines);
    view.dispatch({ effects: EditorView.scrollIntoView(view.state.doc.line(target).from, { y: "center" }) });
    view.focus();
  }, [highlightLine, highlightNonce, sourceCode]);

  return (
    <CodeMirror
      className={className}
      value={sourceCode}
      height="100%"
      minHeight={minHeight}
      extensions={extensions}
      theme={theme}
      onChange={onChange}
      onCreateEditor={view => {
        viewRef.current = view;
      }}
      basicSetup={{
        lineNumbers: true,
        foldGutter: true,
        highlightActiveLine: true
      }}
    />
  );
}

export type EditorLanguageKind = "python" | "cpp";

export type ContestLanguage = {
  id: number;
  label: string;
  sourceFileName: string;
  editorKind: EditorLanguageKind;
  template: string;
};

export const PYTHON3_LANGUAGE_ID = 71;
export const CPP17_LANGUAGE_ID = 54;
export const DEFAULT_CONTEST_LANGUAGE_ID = PYTHON3_LANGUAGE_ID;

const PYTHON_TEMPLATE = "n = int(input())\nprint(n)\n";
const CPP17_TEMPLATE = `#include <bits/stdc++.h>
using namespace std;

int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);

    int n;
    cin >> n;
    cout << n << '\\n';
    return 0;
}
`;

export const CONTEST_LANGUAGES: ContestLanguage[] = [
  {
    id: CPP17_LANGUAGE_ID,
    label: "C++17",
    sourceFileName: "main.cpp",
    editorKind: "cpp",
    template: CPP17_TEMPLATE
  },
  {
    id: PYTHON3_LANGUAGE_ID,
    label: "Python 3",
    sourceFileName: "main.py",
    editorKind: "python",
    template: PYTHON_TEMPLATE
  }
];

export function contestLanguageById(languageId: number): ContestLanguage {
  return CONTEST_LANGUAGES.find(language => language.id === languageId) ?? CONTEST_LANGUAGES[0];
}

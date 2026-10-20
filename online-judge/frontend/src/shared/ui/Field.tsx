import { forwardRef, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from "react";

export function Field({
  label,
  hint,
  children
}: {
  label: string;
  hint?: ReactNode;
  children: ReactNode;
}) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
      {hint && <small>{hint}</small>}
    </label>
  );
}

export const TextInput = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(function TextInput({ className = "", ...props }, ref) {
  return <input ref={ref} className={`control ${className}`.trim()} {...props} />;
});

export const TextArea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(function TextArea({ className = "", ...props }, ref) {
  return <textarea ref={ref} className={`control control--textarea ${className}`.trim()} {...props} />;
});

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(function Select({ className = "", ...props }, ref) {
  return <select ref={ref} className={`control ${className}`.trim()} {...props} />;
});

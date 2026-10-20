import type { ButtonHTMLAttributes, MouseEvent, ReactNode } from "react";
import { Link, type LinkProps } from "react-router-dom";

type Variant = "primary" | "secondary" | "ghost" | "danger";

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: Variant;
  icon?: ReactNode;
};

export function Button({ className = "", variant = "secondary", icon, children, ...props }: ButtonProps) {
  return (
    <button type="button" className={`ui-button ui-button--${variant} ${className}`} {...props}>
      {icon}
      <span>{children}</span>
    </button>
  );
}

type ButtonLinkProps = LinkProps & {
  variant?: Variant;
  icon?: ReactNode;
  disabled?: boolean;
};

export function ButtonLink({ className = "", variant = "secondary", icon, children, disabled, onClick, tabIndex, ...props }: ButtonLinkProps) {
  function handleClick(event: MouseEvent<HTMLAnchorElement>) {
    if (disabled) {
      event.preventDefault();
      return;
    }
    onClick?.(event);
  }

  return (
    <Link
      className={`ui-button ui-button--${variant} ${disabled ? "is-disabled" : ""} ${className}`}
      aria-disabled={disabled || undefined}
      tabIndex={disabled ? -1 : tabIndex}
      onClick={handleClick}
      {...props}
    >
      {icon}
      <span>{children}</span>
    </Link>
  );
}

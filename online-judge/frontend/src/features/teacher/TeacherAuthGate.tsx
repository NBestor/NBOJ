import { ReactNode } from "react";
import PortalAuthGate from "../admin/PortalAuthGate";

export default function TeacherAuthGate({ children }: { children: ReactNode }) {
  return <PortalAuthGate portal="TEACHER" allowTeacherRegistration>{children}</PortalAuthGate>;
}

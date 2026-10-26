export interface User {
  id: string;
  keycloakId: string | null;
  username: string;
  email: string | null;
  fullName: string;
  type: 'HUMAN' | 'TECHNICAL';
  active: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface Ref {
  id: string;
  name: string;
}

export interface Group {
  id: string;
  name: string;
  description: string | null;
  system: boolean;
  version: number;
  memberCount: number;
}

export interface OrgUnit {
  id: string;
  code: string;
  name: string;
  parentId: string | null;
  version: number;
  memberCount: number;
}

export interface Permission {
  code: string;
  serviceId: string;
  name: string;
  description: string | null;
  active: boolean;
}

export interface Grant {
  id: string;
  permissionCode: string;
  permissionName: string;
  subjectType: 'USER' | 'GROUP';
  subjectId: string;
  subjectName: string | null;
  createdAt: string;
  createdBy: string | null;
}

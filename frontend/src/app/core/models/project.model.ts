export interface ProjectMember {
  email: string;
  canManageTasks: boolean;
}

export interface Project {
  id: number;
  nom: string;
  description: string;
  startDate: string;
  endDate: string;
  ownerEmail: string;
  members: ProjectMember[];
}

export interface ProjectRequest {
  nom: string;
  description: string;
  startDate: string;
  endDate: string;
}

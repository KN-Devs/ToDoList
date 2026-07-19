export interface Project {
  id: number;
  nom: string;
  description: string;
  startDate: string;
  endDate: string;
  ownerEmail: string;
  memberEmails: string[];
}

export interface ProjectRequest {
  nom: string;
  description: string;
  startDate: string;
  endDate: string;
}

export interface IMeResponse {
  userId: string;
  email: string;
  name: string;
  avatarUrl: string | null;
  roles: string[];
  mfaEnabled: boolean;
}

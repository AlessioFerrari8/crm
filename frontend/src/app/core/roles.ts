// I 6 ruoli di sistema seminati da RoleSeeder lato backend
export const ROLE_KEYS = ['owner', 'admin', 'manager', 'agent', 'member', 'viewer'] as const;
export type RoleKey = (typeof ROLE_KEYS)[number];

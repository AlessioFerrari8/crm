import { FilterGroup } from "./filter";

export type CampaignStatus = 'DRAFT' | 'SENDING' | 'PAUSED' | 'SENT';

export interface Campaign {
  id: string;
  name: string;
  subject: string;
  preheader: string | null;
  bodyHtml: string;
  bodyText: string;
  status: CampaignStatus;
  targetObjectKey: string;
  targetFilter: FilterGroup | null;
  startedAt: string | null;
  finishedAt: string | null;
  createdAt: string;
}

export type CampaignInput = Pick<Campaign, 'name' | 'subject' | 'preheader' | 'bodyHtml' | 'bodyText' | 'targetObjectKey' | 'targetFilter'>;

export interface CampaignAudience {
  matched: number;
  sendable: number;
  optedOut: number;
  noEmail: number;
  duplicates: number;
  missingVariables: number;
  sample: string[];
}

export interface CampaignStats {
  pending: number;
  sent: number;
  failed: number;
  skipped: number;
  opened: number;
  clicked: number;
}

export interface NpsSurvey {
  id: string;
  name: string;
  question: string;
  active: boolean;
  cooldownDays: number;
}

export interface NpsStats {
  nps: number | null;
  sent: number;
  answered: number;
  promoters: number;
  passives: number;
  detractors: number;
  responseRatePercent: number;
  tooFewAnswers: boolean;
}

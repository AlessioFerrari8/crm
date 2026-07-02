import { FilterGroup } from './filter';
import { ObjectType } from './object-type';

// entities/Record.java — il motore metadata-driven: i valori dei campi dinamici stanno in `data`.
// objectType è presente quando il backend serializza il Record con la relazione annidata (es.
// nei RecordLink del dettaglio e nella ricerca globale): serve per costruire la rotta /o/{key}/{id}.
export interface RecordItem {
  id: string;
  title: string;
  status: string | null;
  ownerId: string | null;
  data: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
  objectType?: { key: string; label: string };
}

// entities/RecordLink.java (forma usata in RecordDetailResponse outgoing/incoming)
export interface RecordLink {
  id: string;
  relationKey: string;
  source: RecordItem;
  target: RecordItem;
  data: Record<string, unknown> | null;
}

// FilterGroup/FilterCondition vivono in models/filter.ts (allineati al RecordFilterCompiler).

export interface SortSpec {
  field: string;
  dir: 'asc' | 'desc';
}

// dtos/QueryRecordsDto.java
export interface QueryRecordsDto {
  q?: string;
  status?: string;
  page?: number;
  pageSize?: number;
  filter?: FilterGroup;
  sort?: SortSpec[];
}

// dtos/BulkRecordsRequest.java — azione di massa (UPDATE imposta `set` sui record, DELETE soft-delete).
// NB l'enum backend è maiuscolo (Jackson case-sensitive di default): inviare "UPDATE"/"DELETE".
export interface BulkRecordsRequest {
  ids: string[];
  action: 'UPDATE' | 'DELETE';
  set?: Record<string, unknown>;
}

// dtos/BulkResult.java
export interface BulkResult {
  affected: number;
}

// dtos/RecordQueryResponse.java
export interface RecordQueryResponse {
  items: RecordItem[];
  total: number;
  page: number;
  pageSize: number;
  object: ObjectType;
}

// dtos/RecordDetailResponse.java
export interface RecordDetailResponse {
  record: RecordItem;
  outgoing: RecordLink[];
  incoming: RecordLink[];
}

// dtos/UpsertRecordDto.java
export interface UpsertRecordDto {
  title?: string;
  status?: string;
  ownerId?: string;
  data: Record<string, unknown>;
}

// GET /api/records/search ritorna i Record con l'objectType annidato (per linkare al dettaglio)
export interface SearchResult extends RecordItem {
  objectType: { key: string; label: string };
}

// provenienza: entities/Tag.java
export interface Tag {
  id: string;
  name: string;
  color: string | null;
  ownerId: string | null;
  createdAt: string;
}

// provenienza: dtos/CreateTagDto.java
export interface CreateTagDto {
  name: string;
  color?: string | null;
  isPrivate: boolean;
}

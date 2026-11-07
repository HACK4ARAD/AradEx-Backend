package aradexperience.dto.media;

/** O poză încărcată: `url` (public, se pune în imageUrl), `key` (calea în bucket), tipul și mărimea în octeți. */
public record MediaDto(String url, String key, String contentType, long size) {}

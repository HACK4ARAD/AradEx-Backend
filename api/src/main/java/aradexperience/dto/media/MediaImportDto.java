package aradexperience.dto.media;

/** Rezultatul unui pas de import: câte poze s-au copiat acum, câte n-au putut fi descărcate și câte mai sunt de copiat. */
public record MediaImportDto(int imported, int failed, int remaining) {}

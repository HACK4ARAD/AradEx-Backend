package aradexperience.dto.admin;

import aradexperience.enums.BusinessCategory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** docs/API.md §Admin (T14): `POST /city/businesses` — contul de firmă creat de Primărie/Admin (verificat din start). */
public record CityBusinessCreate(@NotBlank @Email @Size(max = 190) String email,
                                 @NotBlank @Size(max = 160) String name,
                                 @NotNull BusinessCategory category,
                                 @NotBlank @Size(max = 250) String address,
                                 @NotBlank @Size(max = 120) String contactName,
                                 @Size(max = 40) String phone) {
}

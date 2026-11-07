package aradexperience.mock;

import aradexperience.dto.auth.LoginRequest;
import aradexperience.dto.auth.RegisterRequest;
import aradexperience.entity.business.Business;
import aradexperience.entity.users.User;
import aradexperience.enums.BusinessCategory;
import aradexperience.enums.Role;
import aradexperience.security.CurrentUser;

/** Date de test pentru AuthService / AuthController. */
public final class UserMock {

    public static final String PASSWORD = "demo1234";
    public static final String HASH = "$2a$10$hash";

    private UserMock() {}

    public static User user(long id, String email, Role role) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        u.setPasswordHash(HASH);
        u.setDisplayName("Ana Popescu");
        u.setRole(role);
        u.setLocale("ro");
        u.setPointsBalance(role == Role.LOCAL ? 270 : 0);
        return u;
    }

    public static User local() {
        return user(2L, "local@demo.ro", Role.LOCAL);
    }

    public static User businessOwner() {
        return user(3L, "firma@demo.ro", Role.BUSINESS);
    }

    public static Business business(long ownerId) {
        Business b = new Business();
        b.setId(1L);
        b.setOwnerUserId(ownerId);
        b.setName("Cafeneaua din Pasaj");
        b.setCategory(BusinessCategory.CAFE);
        b.setAddress("Str. Vasile Alecsandri 1, Arad");
        b.setVerified(true);
        return b;
    }

    public static CurrentUser current(User u) {
        return new CurrentUser(u.getId(), u.getEmail(), u.getRole());
    }

    public static RegisterRequest register(Role role) {
        return new RegisterRequest("Ana@Exemplu.ro ", PASSWORD, " Ana Pop ", role, null);
    }

    public static RegisterRequest registerBusiness(String category) {
        return new RegisterRequest("firma.noua@exemplu.ro", PASSWORD, "Ceainăria din Parc", Role.BUSINESS,
                new RegisterRequest.BusinessCreate("Ceainăria din Parc", category, "Str. Eminescu 5, Arad", 46.18, 21.31));
    }

    public static LoginRequest login(String password) {
        return new LoginRequest("local@demo.ro", password);
    }
}

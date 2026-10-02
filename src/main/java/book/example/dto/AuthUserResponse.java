package book.example.dto;

public class AuthUserResponse {
    private final boolean authenticated;
    private final UserSummary user;

    public AuthUserResponse(boolean authenticated, UserSummary user) {
        this.authenticated = authenticated;
        this.user = user;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public UserSummary getUser() {
        return user;
    }

    public static class UserSummary {
        private final String id;
        private final String name;
        private final String email;
        private final String provider;
        private final String avatarUrl;

        public UserSummary(String id, String name, String email, String provider, String avatarUrl) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.provider = provider;
            this.avatarUrl = avatarUrl;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }

        public String getProvider() {
            return provider;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

    }
}

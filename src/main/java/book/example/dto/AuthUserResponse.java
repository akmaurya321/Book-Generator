package book.example.dto;

import java.util.Set;

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
        private final Set<String> roles;

        public UserSummary(String id, String name, String email, String provider, String avatarUrl, Set<String> roles) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.provider = provider;
            this.avatarUrl = avatarUrl;
            this.roles = Set.copyOf(roles);
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

        public Set<String> getRoles() {
            return roles;
        }

    }
}

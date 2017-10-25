package fi.vm.yti.security.config;

import fi.vm.yti.security.Role;
import fi.vm.yti.security.ShibbolethAuthenticationDetails;
import fi.vm.yti.security.YtiUser;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.unmodifiableMap;
import static java.util.Collections.unmodifiableSet;

public class YtiAuthenticationUserDetailsService implements AuthenticationUserDetailsService<PreAuthenticatedAuthenticationToken> {

    private static final Log log = LogFactory.getLog(YtiAuthenticationUserDetailsService.class);

    private final RestTemplate restTemplate;
    private final String groupmanagementUrl;

    YtiAuthenticationUserDetailsService(String groupmanagementUrl) {
        this.groupmanagementUrl = groupmanagementUrl;
        this.restTemplate = new RestTemplate(new SimpleClientHttpRequestFactory());
    }

    @Override
    public UserDetails loadUserDetails(PreAuthenticatedAuthenticationToken token) throws UsernameNotFoundException {
        ShibbolethAuthenticationDetails shibbolethDetails = (ShibbolethAuthenticationDetails) token.getDetails();

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(this.groupmanagementUrl)
                .path("/public-api/user")
                .queryParam("email", shibbolethDetails.getEmail());

        if (shibbolethDetails.getFirstName() != null && shibbolethDetails.getLastName() != null) {
            uriBuilder.queryParam("firstName", shibbolethDetails.getFirstName());
            uriBuilder.queryParam("lastName", shibbolethDetails.getLastName());
        }

        String getUserUri = uriBuilder.build().toUriString();

        User user = this.restTemplate.getForObject(getUserUri, User.class);

        Map<UUID, Set<Role>> rolesInOrganizations = new HashMap<>();

        for (Organization organization : user.organization) {

            Set<Role> roles = organization.role.stream()
                    .filter(YtiAuthenticationUserDetailsService::isRoleMappableToEnum)
                    .map(Role::valueOf)
                    .collect(Collectors.toSet());

            rolesInOrganizations.put(organization.uuid, unmodifiableSet(roles));
        }

        return new YtiUser(user.email, user.firstName, user.lastName, user.superuser, user.newlyCreated, unmodifiableMap(rolesInOrganizations));
    }

    private static boolean isRoleMappableToEnum(String roleString) {

        boolean contains = Role.contains(roleString);

        if (!contains) {
            log.warn("Cannot map role (" + roleString + ")" + " to role enum");
        }

        return contains;
    }
}

class User {

    public String email;
    public String firstName;
    public String lastName;
    public boolean superuser;
    public boolean newlyCreated;
    public List<Organization> organization;
}

class Organization {

    public UUID uuid;
    public List<String> role;
}
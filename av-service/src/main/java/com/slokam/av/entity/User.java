package com.slokam.av.entity;
import java.time.Instant;
import java.util.*;
/** Read-only Identity response; this application does not own a users table. */
public class User {
 public String id, name, mobile, alternativeMobile, email;
 public Role role=Role.USER;
 public Set<Role> additionalRoles=new HashSet<>();
 public Set<String> verifierShowIds=new HashSet<>();
 public boolean enabled;
 public Instant createdAt, updatedAt, mobileVerifiedAt, emailVerifiedAt;
 public Set<Role> effectiveRoles() { var roles=EnumSet.of(role); roles.addAll(additionalRoles); return Collections.unmodifiableSet(roles); }
}

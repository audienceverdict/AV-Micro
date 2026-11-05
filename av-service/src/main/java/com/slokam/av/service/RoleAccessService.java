package com.slokam.av.service;
import com.slokam.av.client.IdentityClient;
import com.slokam.av.entity.*;
import com.slokam.av.repository.ShowRepository;
import com.slokam.av.exception.custom.ApiException;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class RoleAccessService {
 private final IdentityClient users;
 private final ShowRepository shows;
 public RoleAccessService(IdentityClient users, ShowRepository shows) { this.users=users; this.shows=shows; }
 private User account(String id) { return users.findById(id).orElseThrow(()->new ApiException(404,"USER_NOT_FOUND","User not found")); }
 public void requireTicketScanAccess(String actorId,String showId) {
  var actor=account(actorId);
  if(!actor.enabled) throw new ApiException(403,"FORBIDDEN","Account disabled");
  if(actor.effectiveRoles().contains(Role.ADMIN)) return;
  if(!actor.effectiveRoles().contains(Role.VERIFIER)||!actor.verifierShowIds.contains(showId))
   throw new ApiException(403,"FORBIDDEN","Verifier access to this show is required");
 }
 public List<Show> assignedShows(String actorId) {
  var actor=account(actorId);
  if(!actor.enabled||!actor.effectiveRoles().contains(Role.VERIFIER)) throw new ApiException(403,"FORBIDDEN","Enabled verifier account required");
  return shows.findAllById(actor.verifierShowIds).stream().sorted(Comparator.comparing((Show s)->s.date).thenComparing(s->s.startTime).thenComparing(s->s.id)).toList();
 }
}

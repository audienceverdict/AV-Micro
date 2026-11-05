package com.slokam.av.service;
import com.slokam.av.client.IdentityClient;
import com.slokam.av.entity.*;
import com.slokam.av.repository.ShowRepository;
import com.slokam.av.exception.custom.ApiException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RoleAccessBoundaryTest {
 @Test void verifierUsesCurrentAssignmentsIncludingCancelledShowsAndRevocation(){
  var users=mock(IdentityClient.class);var shows=mock(ShowRepository.class);var access=new RoleAccessService(users,shows);
  var user=new User();user.id="staff";user.enabled=true;user.additionalRoles.add(Role.VERIFIER);user.verifierShowIds.addAll(Set.of("first","cancelled"));
  when(users.findById("staff")).thenAnswer(i->Optional.of(user));
  var first=new Show();first.id="first";first.date=java.time.LocalDate.of(2026,1,1);first.startTime=java.time.LocalTime.NOON;
  var cancelled=new Show();cancelled.id="cancelled";cancelled.date=first.date.plusDays(1);cancelled.startTime=first.startTime;cancelled.status=ShowStatus.CANCELLED;
  when(shows.findAllById(user.verifierShowIds)).thenReturn(List.of(cancelled,first));
  assertEquals(List.of("first","cancelled"),access.assignedShows("staff").stream().map(s->s.id).toList());
  access.requireTicketScanAccess("staff","first");
  assertThrows(ApiException.class,()->access.requireTicketScanAccess("staff","other"));
  user.verifierShowIds.clear();assertThrows(ApiException.class,()->access.requireTicketScanAccess("staff","first"));
  user.enabled=false;assertThrows(ApiException.class,()->access.assignedShows("staff"));
  user.enabled=true;user.additionalRoles.clear();assertThrows(ApiException.class,()->access.assignedShows("staff"));
 }
}

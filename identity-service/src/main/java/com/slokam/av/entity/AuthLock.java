package com.slokam.av.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "auth_locks")
public class AuthLock {
    @Id public Integer id;
}

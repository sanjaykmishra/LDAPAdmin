package com.ldapadmin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "realm_settings")
@Getter
@Setter
@NoArgsConstructor
public class RealmSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "realm_id", nullable = false)
    private Realm realm;

    @Column(name = "key", nullable = false, length = 100)
    private String key;

    @Column(name = "value", nullable = false, length = 500)
    private String value;
}
